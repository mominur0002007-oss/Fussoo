package com.tuplayer.app.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    // Few iterations keep the unit test fast. The app uses PinHasher.ITERATIONS.
    private val fast = 1_000

    @Test
    fun correctPinMatches() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt, fast)
        assertTrue(PinHasher.matches("1234", salt, hash, fast))
    }

    @Test
    fun wrongPinDoesNotMatch() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt, fast)
        assertFalse(PinHasher.matches("1235", salt, hash, fast))
        assertFalse(PinHasher.matches("", salt, hash, fast))
    }

    @Test
    fun saltsDifferBetweenCalls() {
        val a = PinHasher.newSalt()
        val b = PinHasher.newSalt()
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun pinFormatIsFourToEightDigits() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
