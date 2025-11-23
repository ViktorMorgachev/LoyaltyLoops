package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

/**
 * Универсальная обертка для сетевых ответов.
 * Позволяет избавиться от try-catch в ViewModel и четко разделяет
 * успешные данные, бизнес-ошибки (4xx) и технические сбои.
 */
sealed interface NetworkResult<out T> {
    
    data class Success<T>(val data: T) : NetworkResult<T>
    
    data class Error(val code: AppErrorCode, val message: String? = null) : NetworkResult<Nothing>
    
    data class Failure(val exception: Throwable) : NetworkResult<Nothing>
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> {
    return when(this) {
        is NetworkResult.Success -> NetworkResult.Success(transform(data))
        is NetworkResult.Error -> this
        is NetworkResult.Failure -> this
    }
}

inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(data)
    return this
}

inline fun <T> NetworkResult<T>.onError(action: (AppErrorCode, String?) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) {
        action(code, message)
    }
    return this
}

inline fun <T> NetworkResult<T>.onFailure(action: (Throwable) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Failure) action(exception)
    return this
}

