package com.riccardopinato.notificationcontrol.core.notifications

import java.security.MessageDigest

object NotificationHasher {
    fun hash(vararg parts: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val canonical = parts.joinToString(separator = "\u001F") { it.orEmpty() }
        return digest.digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
