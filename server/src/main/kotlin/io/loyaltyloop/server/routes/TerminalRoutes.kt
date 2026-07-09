package io.loyaltyloop.server.routes

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.server.service.RatingService
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.utils.getCurrencyForTimezone
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.getWorkspaceIdOrThrow
import io.loyaltyloop.shared.models.CalculateTransactionRequest
import io.loyaltyloop.shared.models.CreateClientRatingDto
import io.loyaltyloop.shared.models.ProcessTransactionRequest
import io.loyaltyloop.shared.models.ScanQrRequest

// TODO checked
fun Route.terminalRoutes(
    transactionService: TransactionService,
    ratingService: RatingService,
    analyticsService: AnalyticsService,
    accessControlService: AccessControlService
) {
    route("/terminal") {
        authenticate("auth-jwt") {
            
            post("/scan") {
                val cashierUserId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<ScanQrRequest>()
                val timezoneCurrency = call.getCurrencyForTimezone()
                val workspaceId = call.getWorkspaceIdOrThrow()
                accessControlService.requirePointAccess(cashierUserId, workspaceId)
                val response = transactionService.scanQr(cashierUserId, workspaceId, timezoneCurrency, request)
                call.respond(response)
            }

            post("/calculate") {
                val cashierUserId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<CalculateTransactionRequest>()
                val timezoneCurrency = call.getCurrencyForTimezone()
                val workspaceId = call.getWorkspaceIdOrThrow()
                accessControlService.requirePointAccess(cashierUserId, workspaceId)
                val result = transactionService.calculateTransaction(
                    cashierUserId = cashierUserId,
                    tradingPointId = workspaceId,
                    cardId = request.cardId,
                    purchaseAmount = request.purchaseAmount,
                    strategy = request.strategy,
                    estimatedCurrency = timezoneCurrency
                )
                
                call.respond(result)
            }

            post("/process") {
                val cashierUserId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<ProcessTransactionRequest>()
                val timezoneCurrency = call.getCurrencyForTimezone()
                val workspaceId = call.getWorkspaceIdOrThrow()
                accessControlService.requirePointAccess(cashierUserId, workspaceId)

                val result = transactionService.processTransaction(
                    cashierUserId = cashierUserId,
                    tradingPointId = workspaceId,
                    cardId = request.cardId,
                    purchaseAmount = request.purchaseAmount,
                    strategy = request.strategy,
                    estimatedCurrency = timezoneCurrency
                )
                
                call.respond(result)
            }
            
            get("/stats") {
                val cashierUserId = call.getUserIdOrRespond(accessControlService) ?: return@get
                val workspaceId = call.getWorkspaceIdOrThrow()
                val stats = analyticsService.getCashierDailyStats(cashierUserId, workspaceId)
                call.respond(stats)
            }

            post("/rate-client") {
                val cashierUserId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<CreateClientRatingDto>()
                val timezoneCurrency = call.getCurrencyForTimezone()
                val workspaceId = call.getWorkspaceIdOrThrow()
                accessControlService.requirePointAccess(cashierUserId, workspaceId)
                val result = ratingService.rateClient(cashierUserId, request, workspaceId,timezoneCurrency)
                call.respond(result)
            }
        }
    }
}
