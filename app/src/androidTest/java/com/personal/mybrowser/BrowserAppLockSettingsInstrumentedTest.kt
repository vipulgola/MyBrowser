package com.personal.mybrowser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.BrowserAppLockSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserAppLockSettingsInstrumentedTest {
    @Test fun appLockIsOffByDefaultAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "app_lock_settings_instrumented_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val settings = BrowserAppLockSettings(context, name)
            assertFalse(settings.isEnabled())
            assertTrue(settings.setEnabled(true))
            assertTrue(BrowserAppLockSettings(context, name).isEnabled())
            assertTrue(settings.setEnabled(false))
            assertFalse(BrowserAppLockSettings(context, name).isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
