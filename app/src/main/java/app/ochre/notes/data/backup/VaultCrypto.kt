package app.ochre.notes.data.backup

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    private val MAGIC = byteArrayOf(0x4F, 0x43, 0x48, 0x52, 0x45, 0x31) // "OCHRE1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPassphraseException : IOException("Wrong passphrase or damaged backup")

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun keyFrom(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts a stream with an already-derived key. Output is IV followed by ciphertext and tag. */
    fun seal(key: ByteArray, input: InputStream, output: OutputStream) {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        output.write(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        pump(cipher, input, output, encrypting = true)
        output.flush()
    }

    /** Reverses [seal]. Throws [WrongPassphraseException] when the key or payload does not match. */
    fun unseal(key: ByteArray, input: InputStream, output: OutputStream) {
        val iv = ByteArray(IV_BYTES)
        var offset = 0
        while (offset < IV_BYTES) {
            val read = input.read(iv, offset, IV_BYTES - offset)
            if (read < 0) throw WrongPassphraseException()
            offset += read
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        pump(cipher, input, output, encrypting = false)
        output.flush()
    }

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val salt = randomSalt()
        output.write(MAGIC)
        output.write(salt)
        val key = keyFrom(passphrase, salt)
        try {
            seal(key, input, output)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val header = ByteArray(MAGIC.size + SALT_BYTES)
        var offset = 0
        while (offset < header.size) {
            val read = input.read(header, offset, header.size - offset)
            if (read < 0) throw WrongPassphraseException()
            offset += read
        }
        for (index in MAGIC.indices) {
            if (header[index] != MAGIC[index]) throw WrongPassphraseException()
        }
        val salt = header.copyOfRange(MAGIC.size, header.size)
        val key = keyFrom(passphrase, salt)
        try {
            unseal(key, input, output)
        } finally {
            key.fill(0)
        }
    }

    private fun pump(cipher: Cipher, input: InputStream, output: OutputStream, encrypting: Boolean) {
        val buffer = ByteArray(BUFFER)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                cipher.update(buffer, 0, read)?.let(output::write)
            }
            output.write(cipher.doFinal())
        } catch (error: Throwable) {
            if (!encrypting && error !is IOException) throw WrongPassphraseException()
            throw error
        }
    }

    private const val BUFFER = 64 * 1024
}