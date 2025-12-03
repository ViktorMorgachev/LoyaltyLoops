package io.loyaltyloop.app.data

import io.loyaltyloop.shared.models.PublicConfigResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigStore {
    private val _config = MutableStateFlow<PublicConfigResponse?>(null)
    val config = _config.asStateFlow()

    fun update(newConfig: PublicConfigResponse) {
        _config.value = newConfig
    }

    fun get(): PublicConfigResponse? = _config.value
}