package app.ochre.notes.data.backup

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {

    private val payload = ("Ochre keeps your words safe. ".repeat(500)).toByteArray(Charsets.UTF_8)

    @Test
    fun `passphrase roundtrip preserves every byte`() {
        val encrypted = ByteArrayOutputStream()
        VaultCrypto.encrypt(payload.inputStream(), encrypted, "correct-horse".toCharArray())
        val bytes = encrypted.toByteArray()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Ochre keeps"))

        val restored = ByteArrayOutputStream()
        VaultCrypto.decrypt(bytes.inputStream(), restored, "correct-horse".toCharArray())
        assertArrayEquals(payload, restored.toByteArray())
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val encrypted = ByteArrayOutputStream()
        VaultCrypto.encrypt(payload.inputStream(), encrypted, "first-passphrase".toCharArray())
        assertThrows(VaultCrypto.WrongPassphraseException::class.java) {
            VaultCrypto.decrypt(encrypted.toByteArray().inputStream(), ByteArrayOutputStream(), "second-passphrase".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = ByteArrayOutputStream()
        VaultCrypto.encrypt(payload.inputStream(), encrypted, "correct-horse".toCharArray())
        val bytes = encrypted.toByteArray()
        bytes[bytes.lastIndex - 1] = (bytes[bytes.lastIndex - 1].toInt() xor 0x01).toByte()
        assertThrows(VaultCrypto.WrongPassphraseException::class.java) {
            VaultCrypto.decrypt(bytes.inputStream(), ByteArrayOutputStream(), "correct-horse".toCharArray())
        }
    }

    @Test
    fun `derived key seals repeated objects under one salt`() {
        val salt = VaultCrypto.randomSalt()
        val key = VaultCrypto.keyFrom("sync-passphrase".toCharArray(), salt)
        val first = ByteArrayOutputStream().also { VaultCrypto.seal(key, payload.inputStream(), it) }
        val second = ByteArrayOutputStream().also { VaultCrypto.seal(key, payload.inputStream(), it) }
        assertFalse(first.toByteArray().contentEquals(second.toByteArray()))

        val restored = ByteArrayOutputStream()
        VaultCrypto.unseal(key, second.toByteArray().inputStream(), restored)
        assertArrayEquals(payload, restored.toByteArray())
    }

    @Test
    fun `salt is random per call`() {
        assertEquals(16, VaultCrypto.randomSalt().size)
        assertFalse(VaultCrypto.randomSalt().contentEquals(VaultCrypto.randomSalt()))
    }
}