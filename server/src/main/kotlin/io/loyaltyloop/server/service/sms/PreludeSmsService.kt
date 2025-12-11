package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.models.VerificationSignals
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.UserRole
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import so.prelude.sdk.client.PreludeClient
import so.prelude.sdk.core.JsonValue
import so.prelude.sdk.models.VerificationCheckParams
import so.prelude.sdk.models.VerificationCheckResponse
import so.prelude.sdk.models.VerificationCreateParams
import java.io.IOException


class PreludeSmsService(
    private val apiKey: String,
    private val preludeClient: PreludeClient,
    private val eventLogger: EventLogger,
    private val emailService: EmailService,
) : SmsService {
    private val client = OkHttpClient()

    private val logger = LoggerFactory.getLogger("PreludeNotify")
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
        val preferredChannel = when {
            phone.startsWith("+996") -> "whatsapp" // Kyrgyzstan -> WhatsApp
            else -> throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
        }

        val jsonBody = buildJsonObject {
            putJsonObject("to") {
                put("type", "phone_number")
                put("value", phone)
            }
            put("text", text)
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
                        logger.error("Prelude Notify critical error: code=$code, body=$bodyStr")
                        eventLogger.log(
                            type = SystemEventType.ERROR,
                            payload = "Prelude notify critical error code=$code for phone=$phone"
                        )
                        notifyAdmins(code ?: "insufficient_balance", bodyStr)
                        throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
                    }
                    if (code in userCodes) {
                        logger.warn("Prelude Notify user-facing error: code=$code, body=$bodyStr")
                        throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
                    }
                    logger.error("Failed to send message via Prelude (Channel: $preferredChannel): ${response.code} $bodyStr")
                    throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
                } else {
                    true
                }
            }
        } catch (e: IOException) {
            logger.error("Exception sending message via Prelude", e)
            throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
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

        // Smart Channel Selection
        // Based on region, we can suggest a preferred channel to Prelude.
        // NOTE: Verify usually optimizes this automatically, but we can hint preference if API supports it.
        // Using "preferred_channel" similar to Notify API.
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
        } catch (e: Exception) {
            handlePreludeError(e, phone)
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

    private suspend fun handlePreludeError(e: Exception, phone: String): String {
        val message = e.message.orEmpty()
        val code = extractPreludeCode(message)
        if (code in criticalCodes) {
            logger.error("Prelude critical error: code=$code, message=$message")
            eventLogger.log(
                type = SystemEventType.ERROR,
                payload = "Prelude critical error code=$code for phone=$phone: $message"
            )
            notifyAdmins(code ?: "insufficient_balance", message)
            throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
        }
        if (code in userCodes) {
            logger.warn("Prelude user-facing error: code=$code, message=$message")
            throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, code ?: "try_later")
        }
        logger.warn("Prelude error (non-critical): $message")
        throw LoyaltyException(AppErrorCode.SMS_PROVIDER_ERROR, "try_later")
    }

    private fun extractPreludeCode(message: String): String? {
        // Prelude SDK may include code in message; quick heuristic
        val regex = "\"code\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(message)?.groupValues?.getOrNull(1)
    }

    private suspend fun notifyAdmins(code: String, body: String) {
        val recipients: List<String> = dbQuery {
            val superAdmins = UsersTable
                .selectAll()
                .where { UsersTable.isSuperAdmin eq true }
                .mapNotNull { it[UsersTable.email] }
            val superManagers = SystemStaffTable
                .join(
                    UsersTable,
                    org.jetbrains.exposed.sql.JoinType.INNER,
                    SystemStaffTable.userId,
                    UsersTable.id
                )
                .selectAll()
                .where { SystemStaffTable.role eq UserRole.PLATFORM_SUPER_MANAGER }
                .mapNotNull { it[UsersTable.email] }
            (superAdmins + superManagers).filter { it.isNotBlank() }.distinct()
        }
        if (recipients.isEmpty()) return
        val subject = "Prelude alert: $code"
        val msg = "Critical Prelude error: $code\nBody: $body"
        recipients.forEach { email ->
            emailService.sendEmail(email, subject, msg)
        }
    }
}
