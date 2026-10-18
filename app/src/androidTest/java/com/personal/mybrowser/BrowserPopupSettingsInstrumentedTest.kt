package com.personal.mybrowser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.BrowserPopupSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPopupSettingsInstrumentedTest {
    @Test fun blockedByDefaultAndChoicePersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "popup_setting_instrumented_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val settings = BrowserPopupSettings(context, name)
            assertFalse(settings.isEnabled())
            assertTrue(settings.setEnabled(true))
            assertTrue(BrowserPopupSettings(context, name).isEnabled())
            assertTrue(settings.setEnabled(false))
            assertFalse(BrowserPopupSettings(context, name).isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
