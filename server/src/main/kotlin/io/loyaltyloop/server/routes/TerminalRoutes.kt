package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse

fun Route.terminalRoutes(repository: UserRepository) {
    route("/terminal") {
        authenticate("auth-jwt") {
            
            // СКАНИРОВАНИЕ QR
            post("/scan") {
                // 1. Кто сканирует? (Проверка прав)
                val principal = call.principal<JWTPrincipal>()
                val cashierId = principal?.payload?.getClaim("id")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()

                // ВАЖНО: В токене кассира (когда он войдет в режим терминала)
                // мы должны будем зашить partnerId или tradingPointId.
                // Пока допустим, мы найдем это через базу.
                
                // (Упрощение для MVP: считаем, что любой юзер может сканировать для теста, 
                // но в реальности тут проверка if (role != CASHIER))
                
                // Получим "текущее место работы" кассира
                // Хардкод для теста: Предположим кассир работает в PartnerId="test_partner"
                // В будущем: достаем из токена или базы
                val currentPartnerId = "test_partner_id_1" // <-- ЗАГЛУШКА

                val request = call.receive<ScanQrRequest>()
                
                // 2. Парсим QR
                // Формат: "loyalty_v1:USER_ID:TIMESTAMP"
                val parts = request.qrContent.split(":")
                if (parts.size != 3 || parts[0] != "loyalty_v1") {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("Invalid QR Format"))
                    return@post
                }
                
                val userId = parts[1]
                val timestamp = parts[2].toLongOrNull() ?: 0L
                
                // TODO: Проверка времени (timestamp) и подписи (HMAC)
                
                // 3. Ищем клиента
                val client = repository.getUserById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Client not found"))
                    return@post
                }

                // 4. ПРИВЯЗКА (Магия)
                // Ищем карту или создаем новую для этого партнера
                // Тут нам нужно немного поправить репозиторий, чтобы он вернул данные
                // (Предполагаем, что метод написан выше)
                 val card = repository.findOrCreateCard(userId, currentPartnerId)
                
                // Заглушка ответа, пока не допишем репозиторий до конца
                call.respond(ScanQrResponse(
                    userId = client.id,
                    userPhone = client.phoneNumber,
                    cardId = "new_card_id",
                    currentBalance = 0.0,
                    tierLevel = 1,
                    isNewCard = true
                ))
            }
        }
    }
}