package io.loyaltyloop.app.data.network


// Базовый класс
sealed class AppException : Exception()

// 401: Токен протух
class UnauthorizedException : AppException()

// 4xx: Ошибка валидации или клиента
class ClientException(val errorMessage: String) : AppException() // Тут текст может прийти с бэка

// 5xx: Сервер упал
class ServerException(val code: Int) : AppException()

// Нет интернета
class NetworkException : AppException()

// Остальное
class UnknownException(val originalError: Throwable) : AppException()