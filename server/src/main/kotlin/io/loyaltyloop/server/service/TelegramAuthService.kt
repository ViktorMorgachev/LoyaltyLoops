package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.repository.UserRepository
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

class TelegramAuthService(
    private val authSessionRepository: AuthSessionRepository,
    private val userRepository: UserRepository,
    private val botToken: String,
    val botUsername: String,
) {
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var lastUpdateId = 0L
    private var job: Job? = null

    fun start(autoCleanupSessionInMillis: Long = 60_000) {
        if (botToken.isBlank()) {
            logger.warn("⚠️ Telegram Bot Token is missing. Telegram Auth skipped.")
            return
        }
        job = CoroutineScope(Dispatchers.IO).launch {
            logger.info("🚀 Telegram Auth Service started.")
            var lastCleanupTime = System.currentTimeMillis()
            while (isActive) {
                try {
                    // Cleanup expired sessions every minute
                    if (System.currentTimeMillis() - lastCleanupTime > autoCleanupSessionInMillis) {
                        authSessionRepository.cleanupExpiredSessions()
                        lastCleanupTime = System.currentTimeMillis()
                    }

                    val updates = getUpdates(lastUpdateId + 1)
                    updates.forEach { update ->
                        val updateId = update["update_id"]?.jsonPrimitive?.long ?: 0L
                        if (updateId >= lastUpdateId) lastUpdateId = updateId
                        processUpdate(update)
                    }
                } catch (e: Exception) {
                    logger.error("Telegram Polling Error: ${e.message}")
                    delay(5000)
                }
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun getUpdates(offset: Long): List<JsonObject> {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset&timeout=10"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            if (root["ok"]?.jsonPrimitive?.boolean != true) return emptyList()
            return root["result"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        }
    }

    private suspend fun processUpdate(update: JsonObject) {
        val message = update["message"]?.jsonObject ?: return
        val chatId = message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.long ?: return
        val text = message["text"]?.jsonPrimitive?.content
        val contact = message["contact"]?.jsonObject

        // 1. /start login_UUID
        if (text?.startsWith("/start login_") == true) {
            val uuid = text.removePrefix("/start login_").trim()
            handleStartLogin(chatId, uuid)
        }

        // 2. Contact
        if (contact != null) {
            handleContact(chatId, contact)
        }
    }

    private suspend fun handleStartLogin(chatId: Long, uuid: String) {
        val user = userRepository.getUserByTelegramId(chatId)
        if (user != null) {
            // Instant login
            authSessionRepository.confirmSession(uuid, chatId, user.phoneNumber, user.id)
            sendMessage(chatId, "✅ Success! You are logged in.")
        } else {
            // Need phone. Link session to chat_id for now.
            dbQuery {
                AuthSessionsTable.update({ AuthSessionsTable.id eq uuid }) {
                    it[telegramId] = chatId
                }
            }
            sendContactRequest(chatId, "👋 Hello! To log in, please tap the button below to share your phone number.")
        }
    }

    private suspend fun handleContact(chatId: Long, contact: JsonObject) {
        val contactUserId = contact["user_id"]?.jsonPrimitive?.long
        if (contactUserId != chatId) {
            sendMessage(chatId, "⚠️ Please share your own contact.")
            return
        }
        val rawPhone = contact["phone_number"]?.jsonPrimitive?.content ?: return
        val phone = if (rawPhone.startsWith("+")) rawPhone else "+$rawPhone"

        // Find pending session for this chat
        val session = dbQuery {
             AuthSessionsTable.select {
                (AuthSessionsTable.telegramId eq chatId) and
                (AuthSessionsTable.status eq "PENDING")
            }.orderBy(AuthSessionsTable.createdAt to SortOrder.DESC)
            .limit(1)
            .map { it[AuthSessionsTable.id] }
            .singleOrNull()
        }

        if (session == null) {
            sendMessage(chatId, "❌ No pending login session found. Scan the QR code again.")
            return
        }

        var user = userRepository.getUserByPhone(phone)
        
        if (user == null) {
             // Create new user if not found
             val newUserId = java.util.UUID.randomUUID().toString()
             val newUser = UserDto(
                 id = newUserId,
                 phoneNumber = phone,
                 countryCode = "KG", // Default
                 firstName = contact["first_name"]?.jsonPrimitive?.content,
                 lastName = contact["last_name"]?.jsonPrimitive?.content,
                 qrSecret = io.loyaltyloop.server.utils.SecurityUtils.generateToken(), // Generate QR secret
                 telegramId = chatId
             )
             userRepository.createUser(newUser)
             user = newUser
        } else {
             if (user.telegramId == null) {
                userRepository.linkTelegram(user.id, chatId)
            }
        }
        
        authSessionRepository.confirmSession(session, chatId, phone, user.id)
        sendMessage(chatId, "✅ Phone verified! You are logged in.")
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

    private fun sendContactRequest(chatId: Long, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val keyboard = JsonObject(mapOf(
            "keyboard" to JsonArray(listOf(
                JsonArray(listOf(
                    JsonObject(mapOf(
                        "text" to JsonPrimitive("📱 Share Contact"),
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

