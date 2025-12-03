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
            Logger.e(it) { "Failed to open realtime socket" }
        }.getOrNull()

        if (opened == null) {
            return false
        }

        session = opened

        listenJob = scope.launch {
            val ws = session ?: return@launch
            try {
                for (frame in ws.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    try {
                        val payload = jsonParser.decodeFromString<CardRealtimePayload>(text)
                        payload.toAnimationMessages().forEach { _events.emit(it) }
                    } catch (error: Exception) {
                        Logger.e(error) { "Failed to parse realtime payload" }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Realtime socket closed" }
            } finally {
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
                    newVisits = newVisits
                )
            )

            CardRealtimeEventType.CARD_UPDATED -> listOf(
                CardAnimationMessage(
                    cardId = cardId,
                    event = CardAnimationEvent.CardSynced,
                    card = cardSnapshot,
                    newBalance = newBalance,
                    newVisits = newVisits
                )
            )

            CardRealtimeEventType.CARD_DELETED -> listOf(
                CardAnimationMessage(
                    cardId = cardId,
                    event = CardAnimationEvent.CardDeleted,
                    card = cardSnapshot
                )
            )

            CardRealtimeEventType.TRANSACTION -> toTransactionMessages()
        }

    private fun CardRealtimePayload.toTransactionMessages(): List<CardAnimationMessage> {
        val amount = args.firstOrNull()?.toDoubleOrNull() ?: 0.0
        val events = mutableListOf<CardAnimationMessage>()
        when (successType) {
            null -> return emptyList()
            TransactionSuccessType.POINTS_EARNED -> events += CardAnimationMessage(
                cardId,
                CardAnimationEvent.BalanceEarned(amount),
                cardSnapshot,
                newBalance,
                newVisits
            )

            TransactionSuccessType.POINTS_SPENT -> events += CardAnimationMessage(
                cardId,
                CardAnimationEvent.BalanceSpent(amount),
                cardSnapshot,
                newBalance,
                newVisits
            )

            TransactionSuccessType.POINTS_SPENT_EARNED -> {
                val spent = args.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                val earned = args.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                if (spent > 0.0) {
                    events += CardAnimationMessage(
                        cardId,
                        CardAnimationEvent.BalanceSpent(spent),
                        cardSnapshot,
                        newBalance,
                        newVisits
                    )
                }
                if (earned > 0.0) {
                    events += CardAnimationMessage(
                        cardId,
                        CardAnimationEvent.BalanceEarned(earned),
                        cardSnapshot,
                        newBalance,
                        newVisits
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
                    newVisits
                )
            }

            TransactionSuccessType.VISIT_REWARD -> {
                events += CardAnimationMessage(
                    cardId,
                    CardAnimationEvent.RewardUnlocked,
                    cardSnapshot,
                    newBalance,
                    newVisits
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
                    newVisits
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

