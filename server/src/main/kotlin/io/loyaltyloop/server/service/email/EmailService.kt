package io.loyaltyloop.server.service.email

import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.models.ResendEmailRequest
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.json
import io.loyaltyloop.server.utils.string
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

interface EmailService {
    suspend fun sendEmail(to: String, template: EmailTemplate, lang: String? = null)
}

class ConsoleEmailService(
    private val templateService: EmailTemplateService = EmailTemplateService()
) : EmailService {
    private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)

    override suspend fun sendEmail(to: String, template: EmailTemplate, lang: String?) {
        val subject = templateService.buildSubject(template, lang)
        val body = templateService.buildBody(template, lang)
        
        // In production, integrate with SendGrid, AWS SES, Mailgun, etc.
        logger.info(
            """=== EMAIL SENT to $to ===
            To: $to
            Subject: $subject
            Body: $body
            ==================
        """.trimIndent()
        )
        
        if (template is EmailTemplate.PartnerPinResetRequested) {
             EmailDebugStore.capturePinReset(template.resetLink)
        }
    }
}

class ResendEmailService(
    private val config: ApplicationConfig,
    private val okHttpClient: OkHttpClient,
    private val eventLogger: EventLogger,
    private val templateService: EmailTemplateService = EmailTemplateService()
) : EmailService {
    private val apiKey = config.string("email.resend.apiKey", "")
    private val fromAddress = config.string("email.fromAddress", "")

    private val isDev: Boolean = config.bool("email.resend.isDev")

    private val email = "morgachev.v.s@gmail.com"


    override suspend fun sendEmail(to: String, template: EmailTemplate, lang: String?) {
        val subject = templateService.buildSubject(template, lang)
        val body = templateService.buildBody(template, lang)

        try {
            val requestBody = ResendEmailRequest(
                from = fromAddress,
                to = if (isDev) listOf(email) else listOf(to),
                subject = subject,
                html = body
            )

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val content = json.encodeToString(requestBody).toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://api.resend.com/emails")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(content)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                if (isDev){
                    eventLogger.log(type = SystemEventType.EMAIL_SEND_SUCCESS, payload = "📧 Email sent to  $email via Resend")
                } else eventLogger.log(type = SystemEventType.EMAIL_SEND_SUCCESS, payload = "📧 Email sent to  $to via Resend")

            } else {
                eventLogger.log(type = SystemEventType.EMAIL_SEND_ERROR, payload = "❌ Failed to send email to $to. Status: ${response.code}. Body: ${response.body?.string()}")
            }
            response.close()
        } catch (e: Exception) {
            if (isDev){
                eventLogger.log(type = SystemEventType.EMAIL_SEND_ERROR, payload = "❌ Exception sending email to $email ${e.message}")
            } else
                eventLogger.log(type = SystemEventType.EMAIL_SEND_ERROR, payload = "❌ Exception sending email to $to, ${e.message}")
        }
    }
}

