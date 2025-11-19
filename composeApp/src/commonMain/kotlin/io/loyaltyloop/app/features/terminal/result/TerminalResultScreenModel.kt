package io.loyaltyloop.app.features.terminal.result

import cafe.adriel.voyager.core.model.ScreenModel
import io.loyaltyloop.shared.models.ScanQrResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalResultScreenModel(
    private val scanData: ScanQrResponse // Передаем данные при создании
) : ScreenModel {

    data class State(
        val data: ScanQrResponse,
        // Поле для ввода суммы (только для TIERED)
        val purchaseAmount: String = "",
        // Расчетные баллы
        val pointsToAward: Double = 0.0,
        val isLoading: Boolean = false
    )

    private val _state = MutableStateFlow(State(data = scanData))
    val state = _state.asStateFlow()

    fun onAmountChanged(amount: String) {
        // Разрешаем только цифры
        if (amount.all { it.isDigit() }) {
            val value = amount.toDoubleOrNull() ?: 0.0
            val points = value * (_state.value.data.cashbackPercent ?: 0.0)
            
            _state.value = _state.value.copy(
                purchaseAmount = amount,
                pointsToAward = points
            )
        }
    }

    fun onProcessTransaction() {
        // TODO: Отправка запроса на начисление
        // Если VISITS -> шлем +1
        // Если TIERED -> шлем сумму
    }
}