package com.personal.mybrowser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.BrowserHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserHistoryRepositoryInstrumentedTest {
    @Test fun historyPersistsUntilTheUserRemovesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "browser_history_instrumented_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val history = BrowserHistoryRepository(context, name)
            assertTrue(history.record("https://example.com/", "Example"))

            val restored = BrowserHistoryRepository(context, name)
            val savedEntry = restored.entries.single()
            assertEquals("https://example.com/", savedEntry.url)
            assertEquals("Example", savedEntry.title)

            assertTrue(restored.remove(savedEntry.id))
            assertTrue(BrowserHistoryRepository(context, name).entries.isEmpty())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
