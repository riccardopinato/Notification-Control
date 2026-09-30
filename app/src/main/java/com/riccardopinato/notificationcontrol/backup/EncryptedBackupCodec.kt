package com.riccardopinato.notificationcontrol.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupCodec {
    private val magic = byteArrayOf(
        'N'.code.toByte(),
        'C'.code.toByte(),
        'B'.code.toByte(),
        '1'.code.toByte()
    )
    private const val iterations = 180_000
    private const val saltSize = 16
    private const val ivSize = 12
    private const val keyBits = 256
    private const val gcmTagBits = 128
    const val maxEncryptedBytes = 128 * 1024 * 1024

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "Passphrase too short" }
        val random = SecureRandom()
        val salt = ByteArray(saltSize).also(random::nextBytes)
        val iv = ByteArray(ivSize).also(random::nextBytes)
        val kdf = preferredKdf()
        val key = deriveKey(passphrase, salt, kdf)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagBits, iv))
        cipher.updateAAD(magic)
        val encrypted = cipher.doFinal(plain)

        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(magic)
                output.writeByte(kdf.id)
                output.writeInt(iterations)
                output.writeByte(salt.size)
                output.write(salt)
                output.writeByte(iv.size)
                output.write(iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            buffer.toByteArray()
        }
    }

    fun decrypt(input: ByteArray, passphrase: CharArray): ByteArray {
        require(input.size <= maxEncryptedBytes) { "Backup too large" }
        require(passphrase.size >= 8) { "Passphrase too short" }

        return DataInputStream(ByteArrayInputStream(input)).use { data ->
            val readMagic = ByteArray(magic.size)
            data.readFully(readMagic)
            require(readMagic.contentEquals(magic)) { "Unsupported backup format" }

            val kdf = Kdf.fromId(data.readUnsignedByte())
            val storedIterations = data.readInt()
            require(storedIterations in 50_000..500_000) { "Invalid backup parameters" }

            val saltLength = data.readUnsignedByte()
            require(saltLength in 12..64) { "Invalid salt" }
            val salt = ByteArray(saltLength).also(data::readFully)

            val ivLength = data.readUnsignedByte()
            require(ivLength in 12..32) { "Invalid IV" }
            val iv = ByteArray(ivLength).also(data::readFully)

            val cipherLength = data.readInt()
            require(cipherLength in 16..maxEncryptedBytes) { "Invalid payload size" }
            require(cipherLength <= data.available()) { "Truncated backup" }
            val encrypted = ByteArray(cipherLength).also(data::readFully)
            require(data.available() == 0) { "Unexpected trailing data" }

            val key = deriveKey(passphrase, salt, kdf, storedIterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagBits, iv))
            cipher.updateAAD(magic)
            cipher.doFinal(encrypted)
        }
    }

    private fun preferredKdf(): Kdf =
        runCatching { SecretKeyFactory.getInstance(Kdf.PBKDF2_SHA256.algorithm) }
            .fold(
                onSuccess = { Kdf.PBKDF2_SHA256 },
                onFailure = { Kdf.PBKDF2_SHA1 }
            )

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        kdf: Kdf,
        rounds: Int = iterations
    ): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(kdf.algorithm)
        val spec = PBEKeySpec(passphrase, salt, rounds, keyBits)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private enum class Kdf(val id: Int, val algorithm: String) {
        PBKDF2_SHA256(1, "PBKDF2WithHmacSHA256"),
        PBKDF2_SHA1(2, "PBKDF2WithHmacSHA1");

        companion object {
            fun fromId(id: Int): Kdf = entries.firstOrNull { it.id == id }
                ?: error("Unsupported KDF")
        }
    }
}
