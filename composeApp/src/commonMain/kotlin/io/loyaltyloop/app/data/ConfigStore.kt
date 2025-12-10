package io.loyaltyloop.app.data

import io.loyaltyloop.shared.models.PublicConfigResponse
import io.loyaltyloop.shared.models.RatingTagDto
import io.loyaltyloop.shared.models.ClientRatingTag
import io.loyaltyloop.shared.models.ServiceReviewTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigStore {
    private val _config = MutableStateFlow<PublicConfigResponse?>(null)
    val config = _config.asStateFlow()

    fun update(newConfig: PublicConfigResponse) {
        _config.value = newConfig
    }

    fun get(): PublicConfigResponse? = _config.value

    fun clientTagWeights(): Map<String, Double> =
        _config.value?.ratingTags?.client?.associate { it.code to it.weight } ?: emptyMap()

    fun serviceTagWeights(): Map<String, Double> =
        _config.value?.ratingTags?.service?.associate { it.code to it.weight } ?: emptyMap()

    fun clientTagsList(): List<RatingTagDto> =
        _config.value?.ratingTags?.client ?: emptyList()

    fun serviceTagsList(): List<RatingTagDto> =
        _config.value?.ratingTags?.service ?: emptyList()
}