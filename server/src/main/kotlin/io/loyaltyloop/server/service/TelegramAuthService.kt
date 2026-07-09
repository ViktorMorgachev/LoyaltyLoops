package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.models.AuthSessionStatus
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.utils.json
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

// TODO checked
class TelegramAuthService(
    private val authSessionRepository: AuthSessionRepository,
    private val userRepository: UserRepository,
    private val botToken: String,
    val botUsername: String,
    val webBaseUrl: String,
    private val webhookUrl: String? = null,
    private val webhookSecret: String? = null
) {
    val migratedWebBaseUrl = if (webBaseUrl =="https://loyalityloop.up.railway.app") "https://loyaltyloops.app" else webBaseUrl
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // Для отправки ответов долгий таймаут не нужен
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun pingBot() {
        val url = "https://api.telegram.org/bot$botToken/getMe"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.warn("Telegram health-check failed (HTTP ${response.code})")
            }
        }
    }

    fun start(autoCleanupSessionInMillis: Long = 60_000) {

        if (!started.compareAndSet(false, true)) {
            logger.warn("TelegramAuthService already started, skipping second start")
            return
        }

        if (botToken.isBlank()) {
            logger.warn("⚠️ Telegram Bot Token is missing. Telegram Auth skipped.")
            return
        }

        logger.info("🚀 Telegram Auth Service started (webhook mode).")

        // 1. Чистка старых сессий
        scope.launch {
            while (isActive) {
                try {
                    authSessionRepository.cleanupExpiredSessions()
                } catch (e: Exception) {
                    logger.error("Session cleanup error: ${e.message}")
                }
                delay(autoCleanupSessionInMillis)
            }
        }

        // 2. Healthcheck Telegram
        scope.launch {
            while (isActive) {
                try {
                    pingBot()
                    logger.debug("Telegram getMe OK")
                } catch (e: Exception) {
                    logger.error("Telegram health-check failed: ${e.message}")
                }
                delay(5 * 60 * 1000L)
            }
        }

        scope.launch {
            try {
                setupWebhook()
            } catch (e: Exception) {
                logger.error("Failed to set webhook: ${e.message}")
            }
        }
    }

    private fun setupWebhook() {
        if (webhookUrl.isNullOrBlank()) {
            logger.error("Webhook URL is not configured. Telegram login will not work.")
            return
        }
        val fullUrl = if (!webhookSecret.isNullOrBlank()) {
            if (webhookUrl.contains("?")) "$webhookUrl&secret=$webhookSecret" else "$webhookUrl?secret=$webhookSecret"
        } else webhookUrl

        val payload = JsonObject(
            mapOf(
                "url" to JsonPrimitive(fullUrl),
                "allowed_updates" to JsonArray(listOf(JsonPrimitive("message")))
            )
        ).toString()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/setWebhook")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                logger.error("setWebhook failed: code=${response.code}, body=$body")
            } else {
                logger.info("setWebhook OK: $fullUrl")
            }
        }
    }

    private fun getMsg(map: Map<String, String>, lang: String): String = map[lang] ?: map["en"]!!

    private val msgHello = mapOf(
        "en" to "Hello! To log in, please tap the button below to share your phone number.",
        "ru" to "Привет! Чтобы войти, нажмите кнопку ниже для отправки номера телефона.",
        "kk" to "Сәлем! Кіру үшін төмендегі түймені басып, телефон нөміріңізді жіберіңіз.",
        "ky" to "Салам! Кирүү үчүн төмөнкү баскычты басып, телефон номериңизди жөнөтүңүз.",
        "uz" to "Salom! Kirish uchun quyidagi tugmani bosing va telefon raqamingizni yuboring.",
        "be" to "Прывітанне! Каб увайсці, націсніце кнопку ніжэй для адпраўкі нумара тэлефона."
    )
    private val btnShare = mapOf(
        "en" to "📱 Share Contact",
        "ru" to "📱 Отправить контакт",
        "kk" to "📱 Байланыспен бөлісу",
        "ky" to "📱 Байланыш бөлүшүү",
        "uz" to "📱 Kontakt yuborish",
        "be" to "📱 Адправіць кантакт"
    )
    private val msgOwnContact = mapOf(
        "en" to "⚠️ Please share your own contact.",
        "ru" to "⚠️ Пожалуйста, поделитесь своим контактом.",
        "kk" to "⚠️ Өз байланысыңызбен бөлісіңіз.",
        "ky" to "⚠️ Өз байланышыңызды бөлүшүңүз.",
        "uz" to "⚠️ O'z kontaktingizni ulashing.",
        "be" to "⚠️ Калі ласка, падзяліцеся сваім кантактам."
    )
    private val msgNoSession = mapOf(
        "en" to "❌ No pending login session found. Scan the QR code again.",
        "ru" to "❌ Активная сессия не найдена. Отсканируйте QR-код заново.",
        "kk" to "❌ Белсенді сессия табылмады. QR-кодты қайта сканерлеңіз.",
        "ky" to "❌ Активдүү сессия табылган жок. QR-кодду кайра сканерлеңиз.",
        "uz" to "❌ Faol sessiya topilmadi. QR-kodni qayta skanerlang.",
        "be" to "❌ Актыўная сесія не знойдзена. Адскануйце QR-код нанова."
    )
    private val msgSuccess = mapOf(
        "en" to "✅ Login successful! Return to the app to continue.",
        "ru" to "✅ Вход выполнен! Вернитесь в приложение.",
        "kk" to "✅ Кіру сәтті! Қосымшаға оралыңыз.",
        "ky" to "✅ Кирүү ийгиликтүү! Тиркемеге кайтыңыз.",
        "uz" to "✅ Kirish muvaffaqiyatli! Ilovaga qayting.",
        "be" to "✅ Уваход выкананы! Вярніцеся ў дадатак."
    )
    private val btnWeb = mapOf(
        "en" to "🌐 Web Dashboard",
        "ru" to "🌐 Веб-панель",
        "kk" to "🌐 Веб-басқару",
        "ky" to "🌐 Веб-башкаруу",
        "uz" to "🌐 Veb-boshqaruv",
        "be" to "🌐 Вэб-панэль"
    )
    private val btnApp = mapOf(
        "en" to "📱 Mobile App",
        "ru" to "📱 Мобильное приложение",
        "kk" to "📱 Мобильді қосымша",
        "ky" to "📱 Мобилдик тиркеме",
        "uz" to "📱 Mobil ilova",
        "be" to "📱 Мабільны дадатак"
    )

   private suspend fun processUpdate(update: JsonObject) {
        // 1. Проверяем ID обновления (для логов)
        val updateId = update["update_id"]?.jsonPrimitive?.long

        // [Совет] Не логируй весь update в проде, там могут быть личные данные.
        // logger.info("Processing update: $updateId")

        // 2. Достаем сообщение
        val message = update["message"]?.jsonObject
        if (message == null) {
            // Это может быть edited_message, callback_query и т.д. Нам они для входа не нужны.
            return
        }

        val chatId = message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.long
        if (chatId == null) {
            logger.warn("Update $updateId: No chat ID found.")
            return
        }

        // 3. Достаем контент
        val text = message["text"]?.jsonPrimitive?.content
        val contact = message["contact"]?.jsonObject

        // Язык пользователя (полезно для ответа на нужном языке)
        val from = message["from"]?.jsonObject
        val languageCode = from?.get("language_code")?.jsonPrimitive?.content ?: "en"

        // 4. Логика Deep Link (/start login_UUID)
        if (text?.startsWith("/start") == true) {
            // Разбиваем "/start login_12345" на части
            val parts = text.split(" ")
            if (parts.size > 1 && parts[1].startsWith("login_")) {
                val uuid = parts[1].removePrefix("login_").trim()

                logger.info("Auth started from chatId=$chatId with UUID prefix")
                handleStartLogin(chatId, uuid, languageCode)
            }
        }

        // 5. Логика Контакта
        if (contact != null) {
            logger.info("Contact received from chatId=$chatId")
            handleContact(chatId, contact, languageCode)
        }
    }

    /**
     * Вебхук вызывается напрямую из маршрута /auth/telegram/webhook
     */
    suspend fun handleWebhookPayload(payload: String) {
        try {
            val jsonElement = json.parseToJsonElement(payload).jsonObject
            processUpdate(jsonElement)
        } catch (e: Exception) {
            logger.error("Failed to process webhook payload: ${e.message}")
        }
    }

    private suspend fun handleStartLogin(chatId: Long, uuid: String, languageCode: String) {
        logger.info("handleStartLogin: chatId=$chatId, uuid=$uuid")
        try {
            val user = userRepository.getUserByTelegramId(chatId)
            if (user != null) {
                logger.info("User found by telegramId=$chatId (userId=${user.id}). Auto-confirming session.")
                authSessionRepository.confirmSession(uuid, chatId, user.phoneNumber, user.id)
                sendSuccessMessage(chatId, languageCode, uuid)
            } else {
                logger.info("User NOT found by telegramId=$chatId. Updating session with telegramId and requesting contact.")
                val updatedCount = dbQuery {
                    AuthSessionsTable.update({ AuthSessionsTable.id eq UUID.fromString(uuid) }) {
                        it[telegramId] = chatId
                    }
                }
                logger.info("Session updated rows: $updatedCount")
                sendContactRequest(chatId, languageCode)
            }
        } catch (e: Exception) {
            logger.error("Error in handleStartLogin: ${e.message}", e)
        }
    }

    private suspend fun handleContact(chatId: Long, contact: JsonObject, languageCode: String) {
        logger.info("handleContact: chatId=$chatId, contact=$contact")
        val contactUserId = contact["user_id"]?.jsonPrimitive?.long
        if (contactUserId != chatId) {
            logger.warn("Contact user_id ($contactUserId) does not match sender chatId ($chatId). Rejecting.")
            sendMessage(chatId, getMsg(msgOwnContact, languageCode))
            return
        }
        val rawPhone = contact["phone_number"]?.jsonPrimitive?.content ?: return
        val phone = if (rawPhone.startsWith("+")) rawPhone else "+$rawPhone"
        logger.info("Contact phone: $phone")

        val session = dbQuery {
             AuthSessionsTable.select {
                (AuthSessionsTable.telegramId eq chatId) and
                (AuthSessionsTable.status eq AuthSessionStatus.PENDING)
            }.orderBy(AuthSessionsTable.createdAt to SortOrder.DESC)
            .limit(1)
            .map { it[AuthSessionsTable.id].toString() }
            .singleOrNull()
        }

        if (session == null) {
            logger.warn("No PENDING session found for chatId=$chatId")
            sendMessage(chatId, getMsg(msgNoSession, languageCode))
            return
        }
        logger.info("Found pending session: $session")

        var user = userRepository.getUserByPhone(phone)

        if (user == null) {
             logger.info("User not found by phone $phone. Creating new user.")
             val newUser = UserDto(
                 id = "will_ignore",
                 phoneNumber = phone,
                 countryCode = "KG", // Default
                 firstName = contact["first_name"]?.jsonPrimitive?.content,
                 lastName = contact["last_name"]?.jsonPrimitive?.content,
                 qrSecret = SecurityUtils.generateToken(),
                 telegramId = chatId,
                 createdAt = nowUtc().toUtcMillis()
             )
             val newUserId = userRepository.createUser(newUser)
             user = newUser.copy(id = newUserId)
             logger.info("Created new user via Telegram: $newUserId")
        } else {
             logger.info("User found by phone $phone (id=${user.id}). Linking telegramId.")
             if (user.telegramId == null) {
                userRepository.linkTelegram(user.id, chatId)
            }
        }

        logger.info("Confirming session $session for userId=${user.id}")
        authSessionRepository.confirmSession(session, chatId, phone, user.id)
        sendSuccessMessage(chatId, languageCode, session)
    }

    private fun sendSuccessMessage(chatId: Long, languageCode: String, uuid: String) {
        logger.info("Sending success message to chatId=$chatId")
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val keyboard = JsonObject(mapOf(
            "inline_keyboard" to JsonArray(listOf(
                JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive(getMsg(btnWeb, languageCode)),
                        "url" to JsonPrimitive(migratedWebBaseUrl)
                    ))
                )),
                 JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive(getMsg(btnApp, languageCode)),
                        "url" to JsonPrimitive("${migratedWebBaseUrl}/auth?uuid=$uuid")
                    ))
                ))
            ))
        ))

        val jsonBody = JsonObject(mapOf(
            "chat_id" to JsonPrimitive(chatId),
            "text" to JsonPrimitive(getMsg(msgSuccess, languageCode)),
            "reply_markup" to keyboard
        )).toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().close()
            logger.info("Success message sent to $chatId")
        } catch (e: Exception) {
            logger.error("Failed to send success message: ${e.message}")
        }
    }

    private fun sendMessage(chatId: Long, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val jsonBody = JsonObject(mapOf(
            "chat_id" to JsonPrimitive(chatId),
            "text" to JsonPrimitive(text)
        )).toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            logger.error("Failed to send message: ${e.message}")
        }
    }

    private fun sendContactRequest(chatId: Long, languageCode: String) {
        val text = getMsg(msgHello, languageCode)
        val buttonText = getMsg(btnShare, languageCode)

        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val keyboard = JsonObject(mapOf(
            "keyboard" to JsonArray(listOf(
                JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive(buttonText),
                        "request_contact" to JsonPrimitive(true)
                    ))
                ))
            )),
            "resize_keyboard" to JsonPrimitive(true),
            "one_time_keyboard" to JsonPrimitive(true)
        ))

        val jsonBody = JsonObject(mapOf(
            "chat_id" to JsonPrimitive(chatId),
            "text" to JsonPrimitive(text),
            "reply_markup" to keyboard
        )).toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            logger.error("Failed to send contact request: ${e.message}")
        }
    }
}
