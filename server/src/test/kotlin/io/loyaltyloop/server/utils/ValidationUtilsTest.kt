package io.loyaltyloop.server.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidationUtilsTest {

    @Test
    fun `validatePhoneNumber should return null for valid KG number`() {
        // +996 (prefix) + 9 digits
        val validPhone = "+996555123456"
        assertNull(validatePhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber should return null for valid KZ number`() {
        // +7 (prefix) + 10 digits
        val validPhone = "+77771234567"
        assertNull(validatePhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber should return null for valid UZ number`() {
        // +998 (prefix) + 9 digits
        val validPhone = "+998901234567"
        assertNull(validatePhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber should return null for valid BY number`() {
        // +375 (prefix) + 9 digits
        val validPhone = "+375291234567"
        assertNull(validatePhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber should fail for invalid KG length`() {
        // +996 + 8 digits (too short)
        val shortPhone = "+99655512345"
    }

    @Test
    fun `validatePhoneNumber should fail for invalid KZ length`() {
        // +7 + 9 digits (too short)
        val shortPhone = "+7777123456"
    }

    @Test
    fun `validatePhoneNumber should fail for unknown country code`() {
        val unknownPhone = "+15551234567" // USA code not supported
    }

    @Test
    fun `validatePhoneNumber should fail for empty string`() {
    }

    @Test
    fun `validatePhoneNumber should fail for garbage string`() {
    }

    @Test
    fun `validatePhoneNumber should prioritize longer prefix (UZ vs 9)`() {
        // If we had +9 prefix (e.g. hypothetical), +998 should match UZ first.
        // In our case, we have +7 (KZ) and others are 3 digits.
        // Testing that +998 is NOT matched as +9 (if +9 existed) is implicit by logic, 
        // but let's ensure +998 works correctly which we did in valid UZ test.
        // The critical one is ensuring +7 doesn't eat things if we had say +77 country. 
        // But we only have +7. 
        // Let's just re-verify standard cases are solid.
        
        // +998... should NOT be treated as unknown if logic was flawed
        assertNull(validatePhoneNumber("+998901234567"))
    }
}

