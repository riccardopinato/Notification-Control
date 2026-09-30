package com.riccardopinato.notificationcontrol.processing

class ProcessingContext(
    val mode: ProcessingMode,
    val allowSideEffects: Boolean = true
) {
    var critical: Boolean = false
    var suppressLuminous: Boolean = false
    var forceFlash: Boolean = false
    var forceOverlay: Boolean = false
    var followUpDelayMinutes: Int? = null
}
