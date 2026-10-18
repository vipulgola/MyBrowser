package com.personal.mybrowser.browser

import android.content.Context

/** App-wide pop-up preference. New installations start with pop-ups blocked. */
class BrowserPopupSettings(
    context: Context,
    preferencesName: String = "browser_popup_settings",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED, false)

    fun setEnabled(enabled: Boolean): Boolean = preferences.edit().putBoolean(ENABLED, enabled).commit()

    private companion object {
        const val ENABLED = "enabled"
    }
}
