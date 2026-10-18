package com.personal.mybrowser.browser

import android.content.Context

/** Stores whether MyBrowser should require device authentication when it returns to the foreground. */
class BrowserAppLockSettings(
    context: Context,
    name: String = "browser_app_lock_settings",
) {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(APP_LOCK_ENABLED, false)

    fun setEnabled(enabled: Boolean): Boolean = preferences.edit().putBoolean(APP_LOCK_ENABLED, enabled).commit()

    private companion object {
        const val APP_LOCK_ENABLED = "app_lock_enabled"
    }
}
