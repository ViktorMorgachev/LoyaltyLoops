package io.loyaltyloop.app.features.wallet

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.WalletRepository
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock // Можно использовать System.currentTimeMillis, но лучше KMP Clock

class WalletScreenModel(
    private val tokenStorage: TokenStorage,
    private val walletRepository: WalletRepository
) : ScreenModel {

    data class State(
        val qrContent: String = "",
        val secondsRemaining: Int = 30,
        val userName: String = "Клиент", // Потом подтянем реальное имя
        val cards: List<LoyaltyCardDto> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        startQrGenerator()
        loadCards()
    }

    private fun startQrGenerator() {
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (isActive) {
                // 1. Генерируем новый код
                updateQrCode()

                // 2. Запускаем обратный отсчет на 30 секунд
                for (i in 30 downTo 1) {
                    _state.value = _state.value.copy(secondsRemaining = i)
                    delay(1000)
                }
            }
        }
    }


    fun loadCards() {
        screenModelScope.launch {
            // Не показываем лоадер на весь экран, если это просто обновление (PullToRefresh)
            // Но для первого раза можно
            _state.value = _state.value.copy(isLoading = true)

            walletRepository.getMyCards()
                .onSuccess { cards ->
                    _state.value = _state.value.copy(
                        cards = cards,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Не удалось загрузить карты" // Лучше в ресурсы
                    )
                }
        }
    }

    private fun updateQrCode() {
        val userId = tokenStorage.getUserId() ?: return
        val secret = tokenStorage.getQrSecret() ?: return // Если нет секрета, QR не генерим

        val timestamp = Clock.System.now().epochSeconds

        // Данные для подписи
        val data = "$userId:$timestamp"

        // Генерируем подпись
        val signature = CryptoUtils.hmacSha256(secret, data)

        // Итоговая строка: v1 : ID : TIME : SIGNATURE
        val payload = "loyalty_v1:$data:$signature"

        _state.value = _state.value.copy(qrContent = payload)
    }
    
    // Нужно добавить метод getUserId в TokenStorage, если его там нет (мы сохраняли, но геттер могли не сделать)
}