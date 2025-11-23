package io.loyaltyloop.app.features.wallet

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.WalletRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class WalletScreenModel(
    private val tokenStorage: TokenStorage,
    private val walletRepository: WalletRepository
) : ScreenModel {

    // --- КОНТРАКТ ---
    data class State(
        val qrContent: String = "",
        val secondsRemaining: Int = 30,
        val cards: List<LoyaltyCardDto> = emptyList(),
        val isLoading: Boolean = false
        // error убрали, он теперь в Event
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

    private var timerJob: Job? = null

    init {
        startQrGenerator()
        loadCards()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnRefresh -> loadCards()
            is Action.OnQrCodeClicked -> log.write("QR Code opened")
        }
    }

    private fun loadCards() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            walletRepository.getMyCards()
                .onSuccess { cards ->
                    _state.value = _state.value.copy(
                        cards = cards,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    log.write("Failed to load cards", LogType.Error, error)

                    // Маппим ошибку в UiText
                    val errorText = when(error) {
                        is ClientException -> UiText.DynamicString(error.errorMessage)
                        is NetworkException -> UiText.Resource(Res.string.error_network)
                        is ServerException -> UiText.Resource(Res.string.error_server)
                        else -> UiText.Resource(Res.string.error_unknown)
                    }

                    // Отправляем событие
                    _events.send(Event.ShowMessage(errorText, SnackbarType.Error))

                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    private fun startQrGenerator() {
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (isActive) {
                updateQrCode()
                for (i in 30 downTo 1) {
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

        _state.value = _state.value.copy(qrContent = payload)
    }
}