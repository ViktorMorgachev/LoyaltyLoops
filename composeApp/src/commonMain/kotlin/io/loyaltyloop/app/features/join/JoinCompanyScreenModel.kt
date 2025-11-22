package io.loyaltyloop.app.features.join

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class JoinCompanyScreenModel(
    private val repository: PartnerRepository // <-- Используем новый репозиторий
) : ScreenModel {

    // Состояние экрана
    data class State(
        val code: String = "",
        val isLoading: Boolean = false,
        val error: UiText? = null
    )

    // Одноразовые события (Навигация, Снекбары)
    sealed interface Event {
        data object NavigateBack : Event
        data class ShowMessage(val message: String) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onCodeChanged(value: String) {
        // Сбрасываем ошибку при вводе
        _state.value = _state.value.copy(code = value, error = null)
    }

    fun onSubmit() {
        val code = _state.value.code
        if (code.isBlank()) return

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            log.write("Joining company with invite code: $code")

            val result = repository.joinCompany(code)

            result.onSuccess { msg ->
                log.write("Successfully joined: $msg")
                _events.send(Event.ShowMessage(msg))
                _events.send(Event.NavigateBack) // Возвращаемся в профиль
            }.onFailure { error ->
                log.write("Join failed", LogType.Error, error)

                val errorText = when(error) {
                    is ClientException -> UiText.DynamicString(error.errorMessage) // Ошибка от сервера (напр. "Неверный код")
                    is NetworkException -> UiText.Resource(Res.string.error_network)
                    is ServerException -> UiText.Resource(Res.string.error_server)
                    else -> UiText.Resource(Res.string.error_unknown)
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    error = errorText
                )
            }
        }
    }
}