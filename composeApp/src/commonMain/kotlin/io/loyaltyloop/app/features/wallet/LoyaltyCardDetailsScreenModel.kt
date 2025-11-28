package io.loyaltyloop.app.features.wallet

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoyaltyCardDetailsScreenModel(
    private val cardId: String
) : ScreenModel {

    data class State(
        val title: String,
        val subtitle: String
    )

    private val _state = MutableStateFlow(State("Card Details", cardId))
    val state = _state.asStateFlow()
}

