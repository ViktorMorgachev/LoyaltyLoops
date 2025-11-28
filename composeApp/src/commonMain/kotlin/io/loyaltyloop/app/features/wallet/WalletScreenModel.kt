package io.loyaltyloop.app.features.wallet

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.WalletRepository
import io.loyaltyloop.app.services.CardRealtimeService
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.config.SecurityDefaults
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.onError
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import kotlin.math.abs

class WalletScreenModel(
    private val tokenStorage: TokenStorage,
    private val walletRepository: WalletRepository,
    private val realtimeService: CardRealtimeService
) : ScreenModel {

    companion object {
        private const val REALTIME_RETRY_DELAY_MS = 2_000L
    }

    // --- КОНТРАКТ ---
    data class State(
        val qrContent: String = "",
        val secondsRemaining: Int = SecurityDefaults.QR_TOKEN_TTL_SECONDS.toInt(),
        val cards: List<LoyaltyCardDto> = emptyList(),
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data object OnRefresh : Action
        data object OnQrCodeClicked : Action
    }

    sealed interface Event {
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }
    // ----------------

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _cardEvents = MutableSharedFlow<CardAnimationMessage>(extraBufferCapacity = 32)
    val cardEvents = _cardEvents.asSharedFlow()

    private val _celebrationState = MutableStateFlow<CelebrationState?>(null)
    val celebrationState = _celebrationState.asStateFlow()

    private var lastCardsSnapshot: Map<String, LoyaltyCardDto> = emptyMap()

    private var timerJob: Job? = null
    private var realtimeJob: Job? = null
    private var realtimeCollectorJob: Job? = null
    private var reconnectJob: Job? = null
    private var realtimeConnecting = false
    private var realtimeConnected = false
    private var subscribedCardIds: Set<String> = emptySet()

    private val qrLifetimeSeconds = SecurityDefaults.QR_TOKEN_TTL_SECONDS.toInt()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnRefresh -> loadCards()
            is Action.OnQrCodeClicked -> startQrGenerator()
        }
    }

    fun loadCards() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            walletRepository.getMyCards()
                .onSuccess { cards ->
                    emitCardDiffs(cards)
                    _state.update { it.copy(cards = cards, isLoading = false) }
                    connectRealtimeIfNeeded(cards)
                }
                .onFailure { exception ->
                    log.write("Failed to load cards", LogType.Error, exception)
                    _events.send(Event.ShowMessage(UiText.Resource(Res.string.error_network), SnackbarType.Error))
                    _state.update { it.copy(isLoading = false) }
                }
                .onError { code, _ ->
                    log.write("Failed to load cards: $code", LogType.Error)
                    _events.send(Event.ShowMessage(UiText.Resource(code.toResource()), SnackbarType.Error))
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    private suspend fun connectRealtimeIfNeeded(cards: List<LoyaltyCardDto>) {
        val token = tokenStorage.getAccessToken() ?: return
        if (cards.isEmpty()) {
            disconnectRealtime()
            return
        }
        ensureRealtimeCollector()

        val targetIds = cards.map { it.id }
        val idSet = targetIds.toSet()
        if (realtimeConnected && idSet == subscribedCardIds) {
            return
        }

        startRealtimeConnection(token, targetIds, idSet)
    }

    private fun ensureRealtimeCollector() {
        if (realtimeCollectorJob?.isActive == true) return
        realtimeCollectorJob = screenModelScope.launch {
            realtimeService.events.collect { message ->
                _cardEvents.emit(message)
                handleCelebration(message)
                applyRealtimeSnapshot(message)
            }
        }
    }

    private fun startRealtimeConnection(
        token: String,
        ids: List<String>,
        idSet: Set<String>
    ) {
        if (realtimeConnecting) return
        reconnectJob?.cancel()
        realtimeJob?.cancel()
        realtimeConnecting = true
        realtimeJob = screenModelScope.launch {
            val connected = realtimeService.connect(
                token = token,
                cardIds = ids,
                onClosed = { scheduleRealtimeReconnect() }
            )
            if (connected) {
                subscribedCardIds = idSet
                realtimeConnected = true
            } else {
                realtimeConnected = false
                scheduleRealtimeReconnect()
            }
            realtimeConnecting = false
        }
    }

    private fun scheduleRealtimeReconnect() {
        reconnectJob?.cancel()
        realtimeConnected = false
        realtimeConnecting = false
        if (_state.value.cards.isEmpty()) {
            subscribedCardIds = emptySet()
            return
        }
        reconnectJob = screenModelScope.launch {
            delay(REALTIME_RETRY_DELAY_MS)
            connectRealtimeIfNeeded(_state.value.cards)
        }
    }

    private fun disconnectRealtime() {
        reconnectJob?.cancel()
        reconnectJob = null
        subscribedCardIds = emptySet()
        realtimeConnected = false
        realtimeConnecting = false
        realtimeJob?.cancel()
        realtimeJob = null
        realtimeCollectorJob?.cancel()
        realtimeCollectorJob = null
        screenModelScope.launch {
            realtimeService.disconnect()
        }
    }

    private fun applyRealtimeSnapshot(message: CardAnimationMessage) {
        when (message.event) {
            CardAnimationEvent.CardDeleted -> {
                _state.update { current ->
                    val next = current.cards.filterNot { it.id == message.cardId }
                    if (next.size == current.cards.size) return@update current
                    lastCardsSnapshot = next.associateBy { it.id }
                    current.copy(cards = next)
                }
            }

            CardAnimationEvent.CardCreated -> {
                val snapshot = message.card ?: return
                _state.update { current ->
                    if (current.cards.any { it.id == snapshot.id }) return@update current
                    val next = current.cards + snapshot
                    lastCardsSnapshot = next.associateBy { it.id }
                    current.copy(cards = next)
                }
            }

            CardAnimationEvent.CardSynced -> {
                message.card?.let { snapshot ->
                    _state.update { current ->
                        val index = current.cards.indexOfFirst { it.id == snapshot.id }
                        if (index == -1) return@update current
                        val next = current.cards.toMutableList().also { it[index] = snapshot }
                        lastCardsSnapshot = next.associateBy { it.id }
                        current.copy(cards = next)
                    }
                }
            }

            else -> {
                val snapshot = message.card
                val newBalance = message.newBalance
                val newVisits = message.newVisits
                if (snapshot == null && newBalance == null && newVisits == null) return

                _state.update { current ->
                    val index = current.cards.indexOfFirst { it.id == message.cardId }
                    if (index == -1) {
                        if (snapshot == null) return@update current
                        val next = current.cards + snapshot
                        lastCardsSnapshot = next.associateBy { it.id }
                        return@update current.copy(cards = next)
                    }

                    val base = current.cards[index]
                    val updated = snapshot ?: base.copy(
                        balance = newBalance ?: base.balance,
                        visitsCount = newVisits ?: base.visitsCount
                    )
                    if (updated == base) return@update current

                    val nextCards = current.cards.toMutableList().also { it[index] = updated }
                    lastCardsSnapshot = nextCards.associateBy { it.id }
                    current.copy(cards = nextCards)
                }
            }
        }
    }

    /**
     * Calculates deltas between the previously rendered cards and the freshly
     * fetched list to produce lightweight animation events (earn/spend/visits/tier).
     * This keeps the UI responsive even before realtime/WS are plugged in.
     */
    private suspend fun emitCardDiffs(cards: List<LoyaltyCardDto>) {
        val newSnapshot = cards.associateBy { it.id }

        if (lastCardsSnapshot.isEmpty()) {
            lastCardsSnapshot = newSnapshot
            return
        }

        cards.forEach { card ->
            val previous = lastCardsSnapshot[card.id]

            if (previous == null) {
                emitCardUiEvent(
                    CardAnimationMessage(
                        card.id,
                        CardAnimationEvent.CardCreated,
                        card
                    )
                )
                return@forEach
            }

            val balanceDiff = card.balance - previous.balance
            when {
                balanceDiff > 0.0001 -> emitCardUiEvent(
                    CardAnimationMessage(
                        card.id,
                        CardAnimationEvent.BalanceEarned(balanceDiff),
                        card,
                        newBalance = card.balance
                    )
                )

                balanceDiff < -0.0001 -> emitCardUiEvent(
                    CardAnimationMessage(
                        card.id,
                        CardAnimationEvent.BalanceSpent(abs(balanceDiff)),
                        card,
                        newBalance = card.balance
                    )
                )
            }

            val visitDiff = card.visitsCount - previous.visitsCount
            if (visitDiff > 0) {
                val target = card.visitsTarget
                val remainingToReward = target.takeIf { it > 0 }?.let { safeTarget ->
                    when {
                        card.visitsCount <= 0 -> safeTarget
                        card.visitsCount % safeTarget == 0 -> 0
                        else -> safeTarget - (card.visitsCount % safeTarget)
                    }
                }
                emitCardUiEvent(
                    CardAnimationMessage(
                        card.id,
                        CardAnimationEvent.VisitProgress(
                            increment = visitDiff,
                            remainingToReward = remainingToReward
                        ),
                        card,
                        newVisits = card.visitsCount
                    )
                )

                if (target > 0) {
                    val previousCycles = previous.visitsCount / target
                    val newCycles = card.visitsCount / target
                    if (newCycles > previousCycles) {
                        emitCardUiEvent(
                            CardAnimationMessage(
                                card.id,
                                CardAnimationEvent.RewardUnlocked,
                                card,
                                newVisits = card.visitsCount
                            )
                        )
                    }
                }
            }

            if (card.tierLevel > previous.tierLevel) {
                emitCardUiEvent(
                    CardAnimationMessage(
                        card.id,
                        CardAnimationEvent.TierUpgrade(card.tierLevel),
                        card
                    )
                )
            }
        }

        lastCardsSnapshot = newSnapshot
    }

    fun startQrGenerator() {
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (isActive) {
                updateQrCode()
                for (i in qrLifetimeSeconds downTo 1) {
                    _state.value = _state.value.copy(secondsRemaining = i)
                    delay(1000)
                }
            }
        }
    }

    private fun updateQrCode() {
        val userId = tokenStorage.getUserId() ?: return
        val secret = tokenStorage.getQrSecret() ?: return

        val timestamp = Clock.System.now().epochSeconds
        val data = "$userId:$timestamp"
        val signature = CryptoUtils.hmacSha256(secret, data)

        val payload = "loyalty_v1:$data:$signature"

        log.write("updateQrCode: ${payload}")

        _state.value = _state.value.copy(qrContent = payload)
    }

    override fun onDispose() {
        super.onDispose()
        disconnectRealtime()
    }

    private fun emitCardUiEvent(message: CardAnimationMessage) {
        screenModelScope.launch {
            _cardEvents.emit(message)
        }
        handleCelebration(message)
    }

    private fun handleCelebration(message: CardAnimationMessage) {
        val shouldCelebrate = when (message.event) {
            is CardAnimationEvent.BalanceEarned,
            is CardAnimationEvent.BalanceSpent,
            CardAnimationEvent.RewardUnlocked,
            is CardAnimationEvent.VisitProgress,
            CardAnimationEvent.CardCreated,
            is CardAnimationEvent.TierUpgrade -> true
            else -> false
        }
        if (!shouldCelebrate) return

        val card = message.card ?: _state.value.cards.find { it.id == message.cardId } ?: return
        val celebration = CelebrationState.from(card, message.event, message.newBalance, message.newVisits) ?: return
        _celebrationState.value = celebration
    }

    fun consumeCelebration() {
        _celebrationState.value = null
    }
}
