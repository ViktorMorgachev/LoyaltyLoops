package io.loyaltyloop.app.services

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.WebSocketSession
import io.loyaltyloop.app.config.AppConfig
import io.loyaltyloop.app.data.network.jsonParser
import io.loyaltyloop.app.features.wallet.CardAnimationEvent
import io.loyaltyloop.app.features.wallet.CardAnimationMessage
import io.loyaltyloop.shared.models.CardRealtimeEventType
import io.loyaltyloop.shared.models.CardRealtimePayload
import io.loyaltyloop.shared.models.TransactionSuccessType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.loyaltyloop.app.utils.log

class DefaultCardRealtimeService(
    private val httpClient: HttpClient
) : CardRealtimeService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<CardAnimationMessage>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private var session: WebSocketSession? = null
    private var listenJob: kotlinx.coroutines.Job? = null

    override suspend fun connect(
        token: String,
        cardIds: List<String>,
        onClosed: (() -> Unit)?
    ): Boolean {
        disconnect()
        val url = URLBuilder().takeFrom(AppConfig.SERVER_URL).apply {
            protocol = when (protocol) {
                URLProtocol.HTTPS -> URLProtocol.WSS
                URLProtocol.HTTP -> URLProtocol.WS
                else -> protocol
            }
            encodedPath = "/ws/cards"
            parameters.append("token", token)
        }.build()

        val opened = runCatching {
            httpClient.webSocketSession {
                url(url)
            }
        }.onFailure {
            Logger.e(it) { "Failed to open realtime socket: ${it.message}" }
        }.getOrNull()

        if (opened == null) {
            Logger.e { "Connection failed (opened is null)" }
            return false
        }

        Logger.d { "Realtime socket connected successfully" }
        session = opened

        listenJob = scope.launch {
            val ws = session ?: return@launch
            try {
                for (frame in ws.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    Logger.d { "Received Frame: $text" }
                    try {
                        val payload = jsonParser.decodeFromString<CardRealtimePayload>(text)
                        val animationMessages = payload.toAnimationMessages()
                        animationMessages.forEach { _events.emit(it) }
                        Logger.d("Payload Transaction: ${animationMessages.joinToString(", ")}")
                    } catch (error: Exception) {
                        Logger.e(error) { "Failed to parse realtime payload: ${error.message}" }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Realtime socket closed with error" }
            } finally {
                Logger.d { "Realtime socket session ended" }
                session = null
                onClosed?.invoke()
            }
        }

        return true
    }

    override suspend fun disconnect() {
        listenJob?.cancel()
        listenJob = null
        session?.close()
        session = null
    }

    private fun CardRealtimePayload.toAnimationMessages(): List<CardAnimationMessage> =
        when (eventType) {
            CardRealtimeEventType.CARD_CREATED -> listOf(
                CardAnimationMessage(
                    cardId = cardId,
                    event = CardAnimationEvent.CardCreated,
                    card = cardSnapshot,
                    newBalance = newBalance,
                    newVisits = newVisits,
                    tradingPointId = tradingPointId
                )
            )

            CardRealtimeEventType.CARD_UPDATED -> listOf(
                CardAnimationMessage(
                    cardId = cardId,
                    event = CardAnimationEvent.CardSynced,
                    card = cardSnapshot,
                    newBalance = newBalance,
                    newVisits = newVisits,
                    tradingPointId = tradingPointId
                )
            )

            CardRealtimeEventType.CARD_DELETED -> listOf(
                CardAnimationMessage(
                    cardId = cardId,
                    event = CardAnimationEvent.CardDeleted,
                    card = cardSnapshot,
                    tradingPointId = tradingPointId
                )
            )

            CardRealtimeEventType.TRANSACTION -> toTransactionMessages()
        }

    private fun parseAmount(arg: String?): Double {
        if (arg == null) return 0.0
        // Сначала пробуем спарсить как есть
        arg.toDoubleOrNull()?.let { return it }

        // Если не вышло, пробуем взять первое "слово", если формат "0.1 (~85 KGS)"
        val firstPart = arg.split(" ").firstOrNull()
        return firstPart?.toDoubleOrNull() ?: 0.0
    }

    private fun CardRealtimePayload.toTransactionMessages(): List<CardAnimationMessage> {
        val amount = parseAmount(args.firstOrNull())
        val events = mutableListOf<CardAnimationMessage>()
        when (successType) {
            null -> return emptyList()
            TransactionSuccessType.POINTS_EARNED -> events += CardAnimationMessage(
                cardId,
                CardAnimationEvent.BalanceEarned(amount),
                cardSnapshot,
                newBalance,
                newVisits,
                tradingPointId
            )

            TransactionSuccessType.POINTS_SPENT -> events += CardAnimationMessage(
                cardId,
                CardAnimationEvent.BalanceSpent(amount),
                cardSnapshot,
                newBalance,
                newVisits,
                tradingPointId
            )

            TransactionSuccessType.POINTS_SPENT_EARNED -> {
                val spent = parseAmount(args.getOrNull(0))
                val earned = parseAmount(args.getOrNull(1))
                if (spent > 0.0) {
                    events += CardAnimationMessage(
                        cardId,
                        CardAnimationEvent.BalanceSpent(spent),
                        cardSnapshot,
                        newBalance,
                        newVisits,
                        tradingPointId
                    )
                }
                if (earned > 0.0) {
                    events += CardAnimationMessage(
                        cardId,
                        CardAnimationEvent.BalanceEarned(earned),
                        cardSnapshot,
                        newBalance,
                        newVisits,
                        tradingPointId
                    )
                }
            }

            TransactionSuccessType.VISIT_PROGRESS -> {
                val totalVisits = args.getOrNull(0)?.toIntOrNull()
                val target = args.getOrNull(1)?.toIntOrNull()
                val increment = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val remaining = computeRemaining(totalVisits, target)
                events += CardAnimationMessage(
                    cardId,
                    CardAnimationEvent.VisitProgress(
                        increment = increment,
                        remainingToReward = remaining
                    ),
                    cardSnapshot,
                    newBalance,
                    newVisits,
                    tradingPointId
                )
            }

            TransactionSuccessType.VISIT_REWARD -> {
                events += CardAnimationMessage(
                    cardId,
                    CardAnimationEvent.RewardUnlocked,
                    cardSnapshot,
                    newBalance,
                    newVisits,
                    tradingPointId
                )
            }

            TransactionSuccessType.BALANCE_INFO,
            TransactionSuccessType.SUCCESS_DEFAULT,
            TransactionSuccessType.CARD_CREATED,
            TransactionSuccessType.CARD_UPDATED,
            TransactionSuccessType.CARD_DELETED -> {
                events += CardAnimationMessage(
                    cardId,
                    CardAnimationEvent.BalanceEarned(amount),
                    cardSnapshot,
                    newBalance,
                    newVisits,
                    tradingPointId
                )
            }
        }
        return events
    }

    private fun computeRemaining(totalVisits: Int?, target: Int?): Int? {
        if (totalVisits == null || target == null || target <= 0) return null
        if (totalVisits <= 0) return target
        val mod = totalVisits % target
        return if (mod == 0) 0 else target - mod
    }
}

