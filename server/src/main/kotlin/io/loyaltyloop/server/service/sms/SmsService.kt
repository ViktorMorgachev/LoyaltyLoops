package io.loyaltyloop.server.service.sms

interface SmsService {
    suspend fun sendSms(phone: String, text: String): Boolean
}