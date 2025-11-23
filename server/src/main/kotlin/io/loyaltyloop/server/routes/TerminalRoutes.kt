package io.loyaltyloop.server.routes

import io.ktor.server.application.call
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.CalculateTransactionRequest
import io.loyaltyloop.shared.models.ProcessTransactionRequest
import io.loyaltyloop.shared.models.ScanQrRequest

fun Route.terminalRoutes(userRepository: UserRepository, transactionService: TransactionService) {
    route("/terminal") {
        authenticate("auth-jwt") {
            
            post("/scan") {
                val cashierUserId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<ScanQrRequest>()
                
                val response = transactionService.scanQr(cashierUserId, request)
                call.respond(response)
            }

            post("/calculate") {
                val cashierUserId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<CalculateTransactionRequest>()

                val result = transactionService.calculateTransaction(
                    cashierUserId = cashierUserId,
                    tradingPointId = request.tradingPointId,
                    cardId = request.cardId,
                    purchaseAmount = request.purchaseAmount,
                    strategy = request.strategy
                )
                
                call.respond(result)
            }

            post("/process") {
                val cashierUserId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<ProcessTransactionRequest>()

                val result = transactionService.processTransaction(
                    cashierUserId = cashierUserId,
                    tradingPointId = request.tradingPointId,
                    cardId = request.cardId,
                    purchaseAmount = request.purchaseAmount,
                    strategy = request.strategy
                )
                
                call.respond(result)
            }
        }
    }
}
