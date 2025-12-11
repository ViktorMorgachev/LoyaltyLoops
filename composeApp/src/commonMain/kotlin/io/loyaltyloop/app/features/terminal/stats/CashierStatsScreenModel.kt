package io.loyaltyloop.app.features.terminal.stats

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.shared.models.CashierDailyStatsDto
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CashierStatsScreenModel(
    private val repository: PartnerRepository
) : ScreenModel {

    data class State(
        val isLoading: Boolean = true,
        val stats: CashierDailyStatsDto? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        screenModelScope.launch {
            println("CashierStats: loading stats...")
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getCashierStats()
                .onSuccess { stats ->
                    println("CashierStats: loaded $stats")
                    _state.update { it.copy(isLoading = false, stats = stats) }
                }
                .onFailure {
                    println("CashierStats: failed ${it.message}")
                    _state.update { it.copy(isLoading = false, error = "Failed to load stats") }
                }
        }
    }
}

