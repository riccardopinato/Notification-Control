package com.riccardopinato.notificationcontrol.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleController {
    const val DEVICE_LANGUAGE = ""

    fun currentLanguageTag(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
