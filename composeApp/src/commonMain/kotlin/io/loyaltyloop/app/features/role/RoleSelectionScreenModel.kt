package io.loyaltyloop.app.features.role

import cafe.adriel.voyager.core.model.ScreenModel
import io.loyaltyloop.app.data.TokenStorage

class RoleSelectionScreenModel(
    private val tokenStorage: TokenStorage
) : ScreenModel {

    fun onRoleSelected() {
        // Запоминаем, что пользователь сделал выбор
        tokenStorage.setRoleSelected(true)
    }
}