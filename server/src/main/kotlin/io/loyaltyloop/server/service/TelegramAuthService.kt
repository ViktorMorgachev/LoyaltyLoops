package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.models.AuthSessionStatus
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import io.loyaltyloop.shared.models.UserDto
import org.jetbrains.exposed.sql.SortOrder
import java.util.UUID

// TODO checked
class TelegramAuthService(
    private val authSessionRepository: AuthSessionRepository,
    private val userRepository: UserRepository,
    private val botToken: String,
    val botUsername: String,
    val webBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS) // На подключение 10 сек ок
        .readTimeout(70, TimeUnit.SECONDS)    // ВАЖНО: Увеличили с 30 до 70
        .writeTimeout(70, TimeUnit.SECONDS)   // На запись тоже лучше поднять
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    fun start(autoCleanupSessionInMillis: Long = 60_000) {

        if (botToken.isBlank()) {
            logger.warn("⚠️ Telegram Bot Token is missing. Telegram Auth skipped.")
            return
        }

        logger.info("🚀 Telegram Auth Service started.")

        // 0. Удаляем вебхук (на всякий случай, чтобы работал getUpdates)
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/deleteWebhook")
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                logger.warn("Could not delete webhook (ignore if first run): ${e.message}")
            }
        }

        // 1. ФОНОВАЯ ЗАДАЧА: Очистка старых сессий (независимо от Телеграма)
        scope.launch {
            while (isActive) {
                try {
                    authSessionRepository.cleanupExpiredSessions()
                } catch (e: Exception) {
                    logger.error("Session cleanup error: ${e.message}")
                }
                delay(autoCleanupSessionInMillis) // Ждем минуту перед следующей чисткой
            }
        }

        // 2. ОСНОВНАЯ ЗАДАЧА: Long Polling (слушаем сообщения)
        job = scope.launch {
            var lastUpdateId = 0L

            while (isActive) {
                try {
                    // В getUpdates у тебя должен стоять timeout=50 в URL!
                    val updates = getUpdates(lastUpdateId + 1)

                    updates.forEach { update ->
                        val updateId = update["update_id"]?.jsonPrimitive?.long ?: 0L
                        if (updateId >= lastUpdateId) lastUpdateId = updateId

                        // Запускаем обработку в отдельной корутине, чтобы не тормозить поллинг
                        launch {
                            processUpdate(update)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Это НОРМАЛЬНО для Long Polling.
                    // Телеграм просто не прислал данных за 50 сек.
                    // Идем на новый круг без задержки.
                    continue
                } catch (e: Exception) {
                    logger.error("Telegram Polling Error: ${e.message}")
                    delay(4000) // Пауза, чтобы не спамить логами
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
         scope.cancel() // Можно отменить весь скоуп при выключении приложения
    }


    private fun getUpdates(offset: Long): List<JsonObject> {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset&timeout=50"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            if (root["ok"]?.jsonPrimitive?.boolean != true) return emptyList()
            return root["result"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        }
    }

    private fun getMsg(map: Map<String, String>, lang: String): String = map[lang] ?: map["en"]!!

    private val MSG_HELLO = mapOf(
        "en" to "Hello! To log in, please tap the button below to share your phone number.",
        "ru" to "Привет! Чтобы войти, нажмите кнопку ниже для отправки номера телефона.",
        "kk" to "Сәлем! Кіру үшін төмендегі түймені басып, телефон нөміріңізді жіберіңіз.",
        "ky" to "Салам! Кирүү үчүн төмөнкү баскычты басып, телефон номериңизди жөнөтүңүз.",
        "uz" to "Salom! Kirish uchun quyidagi tugmani bosing va telefon raqamingizni yuboring.",
        "be" to "Прывітанне! Каб увайсці, націсніце кнопку ніжэй для адпраўкі нумара тэлефона."
    )
    private val BTN_SHARE = mapOf(
        "en" to "📱 Share Contact",
        "ru" to "📱 Отправить контакт",
        "kk" to "📱 Байланыспен бөлісу",
        "ky" to "📱 Байланыш бөлүшүү",
        "uz" to "📱 Kontakt yuborish",
        "be" to "📱 Адправіць кантакт"
    )
    private val MSG_OWN_CONTACT = mapOf(
        "en" to "⚠️ Please share your own contact.",
        "ru" to "⚠️ Пожалуйста, поделитесь своим контактом.",
        "kk" to "⚠️ Өз байланысыңызбен бөлісіңіз.",
        "ky" to "⚠️ Өз байланышыңызды бөлүшүңүз.",
        "uz" to "⚠️ O'z kontaktingizni ulashing.",
        "be" to "⚠️ Калі ласка, падзяліцеся сваім кантактам."
    )
    private val MSG_NO_SESSION = mapOf(
        "en" to "❌ No pending login session found. Scan the QR code again.",
        "ru" to "❌ Активная сессия не найдена. Отсканируйте QR-код заново.",
        "kk" to "❌ Белсенді сессия табылмады. QR-кодты қайта сканерлеңіз.",
        "ky" to "❌ Активдүү сессия табылган жок. QR-кодду кайра сканерлеңиз.",
        "uz" to "❌ Faol sessiya topilmadi. QR-kodni qayta skanerlang.",
        "be" to "❌ Актыўная сесія не знойдзена. Адскануйце QR-код нанова."
    )
    private val MSG_SUCCESS = mapOf(
        "en" to "✅ Login successful! Return to the app to continue.",
        "ru" to "✅ Вход выполнен! Вернитесь в приложение.",
        "kk" to "✅ Кіру сәтті! Қосымшаға оралыңыз.",
        "ky" to "✅ Кирүү ийгиликтүү! Тиркемеге кайтыңыз.",
        "uz" to "✅ Kirish muvaffaqiyatli! Ilovaga qayting.",
        "be" to "✅ Уваход выкананы! Вярніцеся ў дадатак."
    )
    private val BTN_WEB = mapOf(
        "en" to "🌐 Web Dashboard",
        "ru" to "🌐 Веб-панель",
        "kk" to "🌐 Веб-басқару",
        "ky" to "🌐 Веб-башкаруу",
        "uz" to "🌐 Veb-boshqaruv",
        "be" to "🌐 Вэб-панэль"
    )
    private val BTN_APP = mapOf(
        "en" to "📱 Mobile App",
        "ru" to "📱 Мобильное приложение",
        "kk" to "📱 Мобильді қосымша",
        "ky" to "📱 Мобилдик тиркеме",
        "uz" to "📱 Mobil ilova",
        "be" to "📱 Мабільны дадатак"
    )

    private suspend fun processUpdate(update: JsonObject) {
        val message = update["message"]?.jsonObject ?: return
        val chatId = message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.long ?: return
        val text = message["text"]?.jsonPrimitive?.content
        val contact = message["contact"]?.jsonObject
        
        val from = message["from"]?.jsonObject
        val languageCode = from?.get("language_code")?.jsonPrimitive?.content ?: "en"

        if (text?.startsWith("/start login_") == true) {
            val uuid = text.removePrefix("/start login_").trim()
            handleStartLogin(chatId, uuid, languageCode)
        }

        // 2. Contact
        if (contact != null) {
            handleContact(chatId, contact, languageCode)
        }
    }

    private suspend fun handleStartLogin(chatId: Long, uuid: String, languageCode: String) {
        val user = userRepository.getUserByTelegramId(chatId)
        if (user != null) {
            authSessionRepository.confirmSession(uuid, chatId, user.phoneNumber, user.id)
            sendSuccessMessage(chatId, languageCode, uuid)
        } else {
            dbQuery {
                AuthSessionsTable.update({ AuthSessionsTable.id eq UUID.fromString(uuid) }) {
                    it[telegramId] = chatId
                }
            }
            sendContactRequest(chatId, languageCode)
        }
    }
    
    private suspend fun handleContact(chatId: Long, contact: JsonObject, languageCode: String) {
        val contactUserId = contact["user_id"]?.jsonPrimitive?.long
        if (contactUserId != chatId) {
            sendMessage(chatId, getMsg(MSG_OWN_CONTACT, languageCode))
            return
        }
        val rawPhone = contact["phone_number"]?.jsonPrimitive?.content ?: return
        val phone = if (rawPhone.startsWith("+")) rawPhone else "+$rawPhone"

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
            sendMessage(chatId, getMsg(MSG_NO_SESSION, languageCode))
            return
        }

        var user = userRepository.getUserByPhone(phone)
        
        if (user == null) {
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
             userRepository.createUser(newUser)
             user = newUser
        } else {
             if (user.telegramId == null) {
                userRepository.linkTelegram(user.id, chatId)
            }
        }
        
        authSessionRepository.confirmSession(session, chatId, phone, user.id)
        sendSuccessMessage(chatId, languageCode, session)
    }

    private fun sendSuccessMessage(chatId: Long, languageCode: String, uuid: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val keyboard = JsonObject(mapOf(
            "inline_keyboard" to JsonArray(listOf(
                JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive(getMsg(BTN_WEB, languageCode)),
                        "url" to JsonPrimitive(webBaseUrl)
                    ))
                )),
                 JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive(getMsg(BTN_APP, languageCode)),
                        "url" to JsonPrimitive("${webBaseUrl}/auth?uuid=$uuid")
                    ))
                ))
            ))
        ))

        val jsonBody = JsonObject(mapOf(
            "chat_id" to JsonPrimitive(chatId),
            "text" to JsonPrimitive(getMsg(MSG_SUCCESS, languageCode)),
            "reply_markup" to keyboard
        )).toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
            
        try {
            client.newCall(request).execute().close()
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
        val text = getMsg(MSG_HELLO, languageCode)
        val buttonText = getMsg(BTN_SHARE, languageCode)
        
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