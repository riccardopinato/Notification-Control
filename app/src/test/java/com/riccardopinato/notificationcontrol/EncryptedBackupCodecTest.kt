package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.backup.EncryptedBackupCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptedBackupCodecTest {
    @Test
    fun encryptedBackupRoundTrips() {
        val plain = "private notification vault".toByteArray()
        val passphrase = "correct horse battery staple".toCharArray()

        val encrypted = EncryptedBackupCodec.encrypt(plain, passphrase)
        assertFalse(encrypted.contentEquals(plain))
        assertArrayEquals(
            plain,
            EncryptedBackupCodec.decrypt(encrypted, passphrase)
        )
    }

    @Test(expected = Exception::class)
    fun wrongPassphraseCannotDecrypt() {
        val encrypted = EncryptedBackupCodec.encrypt(
            "vault".toByteArray(),
            "correct-password".toCharArray()
        )
        EncryptedBackupCodec.decrypt(
            encrypted,
            "wrong-password".toCharArray()
        )
    }
}
