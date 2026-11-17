package com.tuplayer.app.vault

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a vault PIN into a salted PBKDF2-HMAC-SHA256 hash. The PIN itself is never stored.
 * Pure Java, so it can be unit tested without Android.
 */
object PinHasher {
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so the check does not leak how many leading bytes matched. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)

    /** A PIN is 4 to 8 digits. Anything else is rejected before it is hashed. */
    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
}
