package io.loyaltyloop.server.service.email

import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.models.ResendEmailRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        
        if (template is EmailTemplate.PinResetRequested) {
             EmailDebugStore.capturePinReset(template.resetLink)
        }
    }
}

class ResendEmailService(
    private val config: ApplicationConfig,
    private val okHttpClient: OkHttpClient,
    private val templateService: EmailTemplateService = EmailTemplateService()
) : EmailService {

    private val logger = LoggerFactory.getLogger(ResendEmailService::class.java)
    private val apiKey = config.property("email.resend.apiKey").getString()
    private val fromAddress = config.property("email.fromAddress").getString()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendEmail(to: String, template: EmailTemplate, lang: String?) {
        val subject = templateService.buildSubject(template, lang)
        val body = templateService.buildBody(template, lang)

        try {
            val requestBody = ResendEmailRequest(
                from = fromAddress,
                to = listOf(to),
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
                logger.info("📧 Email sent to $to via Resend")
            } else {
                logger.error("❌ Failed to send email to $to. Status: ${response.code}. Body: ${response.body?.string()}")
            }
            response.close()
        } catch (e: Exception) {
            logger.error("❌ Exception sending email to $to", e)
        }
    }
}

