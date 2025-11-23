package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.UpdatePartnerRequest

fun Route.partnerRoutes(partnerRepository: PartnerRepository, userRepository: UserRepository) {
    route("/partners") {
        authenticate("auth-jwt") {
            
            post("/create") {
                // 1. Безопасность: Достаем ID из токена
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                
                val request = call.receive<CreatePartnerRequest>()
                
                // Валидация
                if (request.businessName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("Название не может быть пустым"))
                    return@post
                }

                // 2. Создаем
                val partnerId = partnerRepository.createPartner(userId, request)
                
                call.respond(HttpStatusCode.Created, ApiMessage("Бизнес создан: $partnerId"))
            }

            post("/join") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@post

                val request = call.receive<JoinTradingPointRequest>()

                // 1. Ищем точку
                val point = partnerRepository.findTradingPointByInvite(request.inviteCode)
                if (point == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Неверный код приглашения"))
                    return@post
                }

                // 2. Проверяем дубли
                if (partnerRepository.isUserCashierAtPoint(userId, point.id)) {
                    call.respond(HttpStatusCode.Conflict, ApiMessage("Вы уже сотрудник этой точки"))
                    return@post
                }

                // 3. Получаем PartnerID
                val partnerId = partnerRepository.getPartnerIdByPoint(point.id)!!

                // 4. Добавляем
                partnerRepository.addCashier(userId, point.id, partnerId)

                call.respond(HttpStatusCode.OK, ApiMessage("Успешно! Вы присоединились к ${point.name}"))
            }

            get("/points") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@get

                // Логика поиска партнера
                val partners = partnerRepository.getPartnersByOwner(userId)
                val myPartner = partners.firstOrNull()

                if (myPartner == null) {
                    // Если бизнес не найден, возвращаем пустой список, а не 404,
                    // чтобы фронтенд не падал с красной ошибкой, а просто показывал "Нет точек"
                    call.respond(listOf<TradingPointDto>())
                    return@get
                }

                val points = partnerRepository.getPointsByPartnerId(myPartner.id)
                call.respond(points)
            }

            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@get

                val partner = partnerRepository.getPartnerByOwnerId(userId)
                if (partner == null) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Бизнес не найден"))
                    return@get
                }
                call.respond(partner)
            }

            // PUT /partners/me - Обновить настройки
            put("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@put

                val request = call.receive<UpdatePartnerRequest>()
                // Валидация
                if (request.businessName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("Название не может быть пустым"))
                    return@put
                }

                partnerRepository.updatePartner(userId, request)
                call.respond(HttpStatusCode.OK, ApiMessage("Настройки сохранены"))
            }

            post("/points") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@post

                val request = call.receive<CreateTradingPointRequest>()

                // 1. Проверяем, есть ли у юзера бизнес (Partner)
                val partners = partnerRepository.getPartnersByOwner(userId)
                val myPartner = partners.firstOrNull()

                if (myPartner == null) {
                    call.respond(HttpStatusCode.Forbidden, ApiMessage("Сначала создайте бизнес"))
                    return@post
                }

                // 3. Создаем точку (Репозиторий сам создаст дефолтные настройки лояльности)
                partnerRepository.createTradingPoint(
                    partnerId = myPartner.id,
                    request = request,
                )

                call.respond(HttpStatusCode.Created, ApiMessage("Точка создана"))
            }
        }
    }
}