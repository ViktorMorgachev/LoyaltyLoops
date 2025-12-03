package io.loyaltyloop.app.ui.components.map

import io.loyaltyloop.shared.models.TradingPointType
import loyaltyloop.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import loyaltyloop.composeapp.generated.resources.*
import kotlin.math.roundToInt

fun getEmojiForType(type: TradingPointType): String {
    return when (type) {
        TradingPointType.COFFEE_SHOP -> "☕️"
        TradingPointType.RESTAURANT -> "🍽️"
        TradingPointType.RETAIL -> "🛍️"
        TradingPointType.SERVICE -> "🛠️"
        TradingPointType.FLOWERS -> "🌸"
        TradingPointType.GIFTS -> "🎁"
        TradingPointType.CAKES -> "🍰"
        TradingPointType.BARBERSHOP -> "💈"
        TradingPointType.CLOTHING -> "👗"
        TradingPointType.TOYS -> "🧸"
        TradingPointType.CAR_RENTAL -> "🚗"
        TradingPointType.SCOOTER_RENTAL -> "🛵"
        TradingPointType.AUTO_SERVICE -> "🔧"
        TradingPointType.TIRE_SERVICE -> "⚙️"
        TradingPointType.AUTO_PARTS -> "🚙"
        TradingPointType.BANK -> "🏦"
        else -> "📍"
    }
}

fun getLabelResource(type: TradingPointType): StringResource {
    return when (type) {
        TradingPointType.COFFEE_SHOP -> Res.string.map_type_coffee_shop
        TradingPointType.RESTAURANT -> Res.string.map_type_restaurant
        TradingPointType.RETAIL -> Res.string.map_type_retail
        TradingPointType.SERVICE -> Res.string.map_type_service
        TradingPointType.FLOWERS -> Res.string.map_type_flowers
        TradingPointType.GIFTS -> Res.string.map_type_gifts
        TradingPointType.CAKES -> Res.string.map_type_cakes
        TradingPointType.BARBERSHOP -> Res.string.map_type_barbershop
        TradingPointType.CLOTHING -> Res.string.map_type_clothing
        TradingPointType.TOYS -> Res.string.map_type_toys
        TradingPointType.CAR_RENTAL -> Res.string.map_type_car_rental
        TradingPointType.SCOOTER_RENTAL -> Res.string.map_type_scooter_rental
        TradingPointType.AUTO_SERVICE -> Res.string.map_type_auto_service
        TradingPointType.TIRE_SERVICE -> Res.string.map_type_tire_service
        TradingPointType.AUTO_PARTS -> Res.string.map_type_auto_parts
        TradingPointType.BANK -> Res.string.map_type_bank
        else -> Res.string.map_type_other
    }
}

fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        // Округляем до 1 знака после запятой
        val km = (meters / 100.0).roundToInt() / 10.0
        "$km км"
    } else {
        "$meters м"
    }
}