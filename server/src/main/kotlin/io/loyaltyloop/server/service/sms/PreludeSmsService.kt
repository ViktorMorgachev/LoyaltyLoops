package io.loyaltyloop.server.service.sms

import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.models.VerificationSignals
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.service.email.EmailTemplate
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.UserRole
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import so.prelude.sdk.client.PreludeClient
import so.prelude.sdk.client.okhttp.PreludeOkHttpClient
import so.prelude.sdk.core.JsonValue
import so.prelude.sdk.models.VerificationCheckParams
import so.prelude.sdk.models.VerificationCheckResponse
import so.prelude.sdk.models.VerificationCreateParams


// TODO checked
class PreludeSmsService(
    private val config: ApplicationConfig,
    private val eventLogger: EventLogger,
    private val emailService: EmailService,
) : SmsService {
    private val client = OkHttpClient()

    private val isDev: Boolean = config.bool("sms.prelude_conf.isDev", true)

    private val testPhone = "+996554190030"
    private val apiKey = config.string("sms.prelude_conf.apiKey", "")

    private val preludeClient: PreludeClient =  PreludeOkHttpClient.builder().apiToken(apiKey).build()

    private val criticalCodes = setOf("insufficient_balance", "suspended_account")
    private val userCodes = setOf(
        "unsupported_country",
        "channel_not_enabled_in_region",
        "too_many_attempts",
        "too_many_checks",
        "impossible_code"
    )


    override suspend fun sendSms(phone: String, text: String): Boolean {
        // Docs: https://docs.prelude.so/notify/v2/api-reference/send-a-message
        // Endpoint: POST https://api.prelude.dev/v2/messages

        // Smart Channel Selection
        val preferredChannel = "whatsapp"

        val jsonBody = buildJsonObject {
            putJsonObject("to") {
                put("type", "phone_number")
                if (isDev) {
                    put("value", testPhone)
                    put("text", "$text for $phone")
                } else {
                    put("value", phone)
                    put("text", text)
                }
            }

            put("preferred_channel", preferredChannel)
        }.toString()

        val request = Request.Builder()
            .url("https://api.prelude.dev/v2/messages")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val code = extractPreludeCode(bodyStr)
                    if (code in criticalCodes) {
                        notifyAdmins(code ?: "insufficient_balance", bodyStr)
                    }
                    val errorInfo = " (Channel: $preferredChannel), error: code=$code, body=$bodyStr"
                    verificationError(code, errorInfo)

                    throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
                } else {
                    true
                }
            }
        } catch (t: Throwable) {
            handlePreludeError(t, if (isDev) testPhone else phone)
            false
        }
    }

    private  fun verificationError(code: String?, errorInfo: String) {
        if (code in criticalCodes) {
            eventLogger.log(
                type = SystemEventType.SMS_SEND_ERROR,
                payload = "Prelude send message critical $errorInfo"
            )
            throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
        }
        if (code in userCodes) {
            eventLogger.log(type = SystemEventType.SMS_SEND_ERROR, payload = "Prelude send message user-facing $errorInfo")
            throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
        }
    }

    override suspend fun startVerification(
        phone: String,
        userId: String?,
        signals: VerificationSignals?
    ): String {
        val builder = VerificationCreateParams.builder()
            .target(
                VerificationCreateParams.Target.builder()
                    .type(VerificationCreateParams.Target.Type.PHONE_NUMBER)
                    .value(phone)
                    .build()
            )

        // Signals
        val signalsMap = mutableMapOf<String, Any?>()
        signals?.let {
            it.ip?.let { v -> signalsMap["ip"] = v }
            it.deviceId?.let { v -> signalsMap["device_id"] = v }
            it.platform?.let { v -> signalsMap["device_platform"] = v }
            it.deviceModel?.let { v -> signalsMap["device_model"] = v }
            it.osVersion?.let { v -> signalsMap["os_version"] = v }
            it.appVersion?.let { v -> signalsMap["app_version"] = v }
        }
        if (signalsMap.isNotEmpty()) {
            builder.putAdditionalBodyProperty("signals", JsonValue.from(signalsMap))
        }

        val preferredChannel = when {
            phone.startsWith("+996") -> "whatsapp" // Kyrgyzstan
            else -> null // Let Prelude decide (usually SMS)
        }

        if (preferredChannel != null) {
            builder.putAdditionalBodyProperty("preferred_channel", JsonValue.from(preferredChannel))
        }

        return try {
            val response = preludeClient.verification().create(builder.build())
            response.id()
        } catch (t: Throwable) {
            handlePreludeError(t, phone)
        }
    }

    override suspend fun checkCode(
        verificationId: String?,
        phone: String,
        code: String
    ): Boolean {
        return try {
            val params: VerificationCheckParams = VerificationCheckParams.builder()
                .target(
                    VerificationCheckParams.Target.builder()
                        .type(VerificationCheckParams.Target.Type.Companion.PHONE_NUMBER)
                        .value(phone)
                        .build()
                )
                .code(code)
                .build()
            val response = preludeClient.verification().check(params)
            response.status() == VerificationCheckResponse.Status.SUCCESS
        } catch (e: Exception) {
            handlePreludeError(e, phone)
            false
        }
    }

    private suspend fun handlePreludeError(t: Throwable, phone: String): String {
        val message = t.message.orEmpty()
        val code = extractPreludeCode(message)
        val errorInfo = "code=$code for phone=$phone: $message\""
        if (code in criticalCodes) {
            notifyAdmins(code ?: "insufficient_balance", message)
        }
        verificationError(code, errorInfo)
        throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
    }

    private fun extractPreludeCode(message: String): String? {
        // Prelude SDK may include code in message; quick heuristic
        val regex = "\"code\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(message)?.groupValues?.getOrNull(1)
    }


    private suspend fun notifyAdmins(code: String, body: String) {
        val recipients: List<String> = dbQuery {
            val superUsers = SystemStaffTable
                .join(
                    UsersTable,
                    JoinType.INNER,
                    SystemStaffTable.user,
                    UsersTable.id
                )
                .selectAll()
                .where {
                    (SystemStaffTable.role eq UserRole.PLATFORM_SUPER_MANAGER) or
                            (SystemStaffTable.role eq UserRole.PLATFORM_SUPER_ADMIN)
                }
                .mapNotNull { it[UsersTable.email] }
            superUsers.filter { it.isNotBlank() }.distinct()
        }
        if (recipients.isEmpty()) return

        val template = EmailTemplate.SmsProviderAlert(code, body)

        recipients.forEach { email ->
            emailService.sendEmail(email, template, "ru") // System alerts in English
        }
    }
}
