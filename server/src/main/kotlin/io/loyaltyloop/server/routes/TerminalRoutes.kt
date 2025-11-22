package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlin.math.abs

fun Route.terminalRoutes(userRepository: UserRepository, partnerRepository: PartnerRepository) {
    route("/terminal") {
        authenticate("auth-jwt") {
            
            // СКАНИРОВАНИЕ QR
            post("/scan") {
                val principal = call.principal<JWTPrincipal>()
                val cashierUserId = principal?.payload?.getClaim("id")?.asString() ?: return@post

                val request = call.receive<ScanQrRequest>()

                // 1. SECURITY CHECK: Действительно ли юзер работает в этой точке?
                // (Используем метод из PartnerRepo, который мы писали для /join)
                val isWorker = partnerRepository.isUserCashierAtPoint(cashierUserId, request.tradingPointId)

                if (!isWorker) {
                    // Хакерская попытка или старый токен
                    call.respond(HttpStatusCode.Forbidden, ApiMessage("Вы не сотрудник этой точки"))
                    return@post
                }

                // 2. Получаем ID Партнера через точку
                // (Так как карта привязывается к Партнеру, а не к точке)
                val partnerId = partnerRepository.getPartnerIdByPoint(request.tradingPointId)

                if (partnerId == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Торговая точка не найдена"))
                    return@post
                }

                // --- НОВОЕ: ЗАГРУЖАЕМ ДАННЫЕ ПАРТНЕРА ---
                val partner = partnerRepository.getPartnerById(partnerId)
                if (partner == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Партнер не найден"))
                    return@post
                }

                // 3. Парсинг QR
                // 1. Парсинг: loyalty_v1 : userId : timestamp : signature
                val parts = request.qrContent.split(":")
                if (parts.size != 4 || parts[0] != "loyalty_v1") {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("Неверный формат QR"))
                    return@post
                }

                val customerId = parts[1]
                val timestamp = parts[2].toLongOrNull() ?: 0L
                val clientSignature = parts[3]

                // 2. Проверка времени (защита от Replay атак)
                val now = System.currentTimeMillis() / 1000
                // Разрешаем отклонение +/- 60 секунд
                if (abs(now - timestamp) > 60) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("QR-код устарел. Обновите экран."))
                    return@post
                }

                // 3. Проверка подписи
                val customer = userRepository.getUserById(customerId)
                if (customer == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Клиент не найден"))
                    return@post
                }

                val secret = customer.qrSecret
                if (secret.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, ApiMessage("Ошибка безопасности профиля"))
                    return@post
                }

                // Хешируем сами и сверяем
                val data = "$customerId:$timestamp"
                val expectedSignature = CryptoUtils.hmacSha256(secret, data)

                if (expectedSignature != clientSignature) {
                    call.respond(HttpStatusCode.Forbidden, ApiMessage("Поддельный QR-код!"))
                    return@post
                }

                // 5. FIND OR CREATE CARD
                val (card, isCreatedNow) = userRepository.findOrCreateCard(
                    userId = customerId,
                    partnerId = partnerId,
                    partnerName = partner.name,
                    partnerColor = partner.color,
                    partnerLogo = partner.logoUrl,

                )

                // Нам нужно знать, какая стратегия у этой точки, чтобы сказать кассиру.
                val settings = partnerRepository.getSettingsByPointId(request.tradingPointId)

                if (settings == null) {
                    call.respond(HttpStatusCode.InternalServerError, ApiMessage("Настройки лояльности не найдены"))
                    return@post
                }

                // 6. Ответ
                // Определяем текущий % кешбэка (если TIERED)
                // Ищем уровень, соответствующий уровню карты
                val currentTier = settings.tiers.find { it.levelIndex == card.tierLevel }
                val percent = currentTier?.cashbackPercent ?: 0.0

                call.respond(ScanQrResponse(
                    userId = customer.id,
                    userPhone = customer.phoneNumber,
                    firstName = customer.firstName,

                    cardId = card.id,
                    currentBalance = card.balance,
                    visitsCount = card.visitsCount,

                    programType = settings.programType,
                    visitsTarget = settings.visitsTarget,
                    cashbackPercent = percent,

                    isNewCard = isCreatedNow
                ))
            }
        }
    }
}