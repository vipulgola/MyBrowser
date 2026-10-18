package com.personal.mybrowser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.mybrowser.browser.JavaScriptSitePermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptSitePermissionsInstrumentedTest {
    @Test fun blockIsSavedForOnlyTheSelectedHttpsHostnameAndCanBeRevoked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "javascript_site_permissions_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val permissions = JavaScriptSitePermissions(context, name)
            assertTrue(permissions.isAllowed("https://m.youtube.com/"))
            assertTrue(permissions.setAllowed("https://m.youtube.com/", false))

            val reopened = JavaScriptSitePermissions(context, name)
            assertFalse(reopened.isAllowed("https://m.youtube.com/watch?v=1"))
            assertTrue(reopened.isAllowed("https://www.youtube.com/"))
            assertTrue(reopened.isAllowed("https://m.youtube.com.attacker.example/"))
            assertTrue(reopened.isAllowed("https://example.com/?next=m.youtube.com"))
            assertFalse(reopened.isAllowed("http://m.youtube.com/"))

            assertTrue(reopened.setAllowed("https://m.youtube.com/", true))
            assertTrue(JavaScriptSitePermissions(context, name).isAllowed("https://m.youtube.com/"))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
