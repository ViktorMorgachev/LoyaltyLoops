package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationUtilsTest {

    // Валидный номер: функция просто возвращается; невалидный: бросает LoyaltyException(INVALID_PHONE)

    @Test
    fun `validatePhoneNumber accepts valid KG number`() {
        // +996 (prefix) + 9 digits
        validatePhoneNumber("+996555123456")
    }

    @Test
    fun `validatePhoneNumber accepts valid KZ number`() {
        // +7 (prefix) + 10 digits
        validatePhoneNumber("+77771234567")
    }

    @Test
    fun `validatePhoneNumber accepts valid UZ number`() {
        // +998 (prefix) + 9 digits
        validatePhoneNumber("+998901234567")
    }

    @Test
    fun `validatePhoneNumber accepts valid BY number`() {
        // +375 (prefix) + 9 digits
        validatePhoneNumber("+375291234567")
    }

    @Test
    fun `validatePhoneNumber fails for invalid KG length`() {
        // +996 + 8 digits (too short)
        assertInvalidPhone("+99655512345")
    }

    @Test
    fun `validatePhoneNumber fails for invalid KZ length`() {
        // +7 + 9 digits (too short)
        assertInvalidPhone("+7777123456")
    }

    @Test
    fun `validatePhoneNumber fails for unknown country code`() {
        assertInvalidPhone("+15551234567") // USA code not supported
    }

    @Test
    fun `validatePhoneNumber fails for empty string`() {
        assertInvalidPhone("")
    }

    @Test
    fun `validatePhoneNumber fails for garbage string`() {
        assertInvalidPhone("not-a-phone")
    }

    @Test
    fun `validatePhoneNumber prioritizes longer prefix over KZ +7`() {
        // +998 должен матчиться как UZ (длинный префикс), а не как +7/неизвестный;
        // обычный KZ-номер при этом остаётся валидным
        validatePhoneNumber("+998901234567")
        validatePhoneNumber("+77012345678")
    }

    private fun assertInvalidPhone(phone: String) {
        val ex = assertFailsWith<LoyaltyException> { validatePhoneNumber(phone) }
        assertEquals(AppErrorCode.INVALID_PHONE, ex.code)
    }
}
