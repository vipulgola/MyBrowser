package com.personal.mybrowser.browser

import android.content.Context

/** Tor is required on new installations until the user explicitly turns it off. */
class BrowserTorSettings(
    context: Context,
    preferencesName: String = "browser_tor_settings",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED, true)

    fun setEnabled(enabled: Boolean): Boolean = preferences.edit().putBoolean(ENABLED, enabled).commit()

    private companion object {
        const val ENABLED = "enabled"
    }
}
