package com.riccardopinato.notificationcontrol.processing

import java.security.MessageDigest

object VaultEventIdentity {
    fun initialKey(platformKey: String, postedAt: Long): String =
        sha256("$platformKey\u0000$postedAt")

    fun collisionKey(
        platformKey: String,
        postedAt: Long,
        capturedAt: Long
    ): String = sha256("$platformKey\u0000$postedAt\u0000$capturedAt")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
