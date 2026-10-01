package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.data.AutomationDao
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileDao
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.RuleWithActions
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationRuntimeCache(
    scope: CoroutineScope,
    automationDao: AutomationDao,
    luminousProfileDao: LuminousProfileDao
) {
    private val rules = AtomicReference<List<RuleWithActions>>(emptyList())
    private val criticalPatterns =
        AtomicReference<List<CriticalPatternEntity>>(emptyList())
    private val luminousProfiles =
        AtomicReference<List<LuminousProfileEntity>>(emptyList())

    private val rulesReady = CompletableDeferred<Unit>()
    private val criticalReady = CompletableDeferred<Unit>()
    private val luminousReady = CompletableDeferred<Unit>()

    init {
        scope.launch {
            automationDao.observeEnabledRules().collectLatest {
                rules.set(it)
                rulesReady.complete(Unit)
            }
        }
        scope.launch {
            automationDao.observeEnabledCriticalPatterns().collectLatest {
                criticalPatterns.set(it)
                criticalReady.complete(Unit)
            }
        }
        scope.launch {
            luminousProfileDao.observeEnabledProfiles().collectLatest {
                luminousProfiles.set(it)
                luminousReady.complete(Unit)
            }
        }
    }

    suspend fun enabledRules(): List<RuleWithActions> {
        rulesReady.await()
        return rules.get()
    }

    suspend fun enabledCriticalPatterns(): List<CriticalPatternEntity> {
        criticalReady.await()
        return criticalPatterns.get()
    }

    suspend fun enabledLuminousProfiles(): List<LuminousProfileEntity> {
        luminousReady.await()
        return luminousProfiles.get()
    }

    suspend fun hasEnabledLuminousProfiles(): Boolean =
        enabledLuminousProfiles().isNotEmpty()
}
