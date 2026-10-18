package com.personal.mybrowser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.BrowserTorSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserTorSettingsInstrumentedTest {
    @Test fun torIsOnByDefaultAndChoicePersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tor_setting_instrumented_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val settings = BrowserTorSettings(context, name)
            assertTrue(settings.isEnabled())
            assertTrue(settings.setEnabled(false))
            assertFalse(BrowserTorSettings(context, name).isEnabled())
            assertTrue(settings.setEnabled(true))
            assertTrue(BrowserTorSettings(context, name).isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
