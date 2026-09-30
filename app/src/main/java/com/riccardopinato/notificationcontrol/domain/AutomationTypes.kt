package com.riccardopinato.notificationcontrol.domain

object RuleActionType {
    const val FLASH = "FLASH"
    const val OVERLAY = "OVERLAY"
    const val CRITICAL = "CRITICAL"
    const val FOLLOW_UP = "FOLLOW_UP"
}

object CriticalPatternType {
    const val APP = "APP"
    const val SENDER = "SENDER"
    const val KEYWORD = "KEYWORD"
}
