package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.processing.VaultEventIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VaultEventIdentityTest {
    @Test
    fun samePlatformLifecycleHasStableInitialKey() {
        assertEquals(
            VaultEventIdentity.initialKey("platform", 100L),
            VaultEventIdentity.initialKey("platform", 100L)
        )
    }

    @Test
    fun reusedPlatformKeyWithDifferentPostTimeGetsDifferentEventKey() {
        assertNotEquals(
            VaultEventIdentity.initialKey("platform", 100L),
            VaultEventIdentity.initialKey("platform", 200L)
        )
    }

    @Test
    fun collisionFallbackCreatesIndependentEventInstance() {
        assertNotEquals(
            VaultEventIdentity.initialKey("platform", 100L),
            VaultEventIdentity.collisionKey("platform", 100L, 150L)
        )
    }
}
