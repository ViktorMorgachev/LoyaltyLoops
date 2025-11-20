package io.loyaltyloop.app.features.wallet

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock // Можно использовать System.currentTimeMillis, но лучше KMP Clock

class WalletScreenModel(
    private val tokenStorage: TokenStorage
) : ScreenModel {

    data class State(
        val qrContent: String = "",
        val secondsRemaining: Int = 30,
        val userName: String = "Клиент" // Потом подтянем реальное имя
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        startQrGenerator()
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

    private fun updateQrCode() {
        // Формат QR: "loyalty_v1:<userId>:<timestamp>"
        // Сервер при сканировании проверит timestamp и отклонит старый код
        val userId = tokenStorage.getUserId() ?: "unknown"
        val timestamp = Clock.System.now().epochSeconds
        
        val payload = "loyalty_v1:$userId:$timestamp"
        
        _state.value = _state.value.copy(qrContent = payload)
    }
    
    // Нужно добавить метод getUserId в TokenStorage, если его там нет (мы сохраняли, но геттер могли не сделать)
}