package com.personal.mybrowser.browser

import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.network.AddressValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PageFormTest {
    private val parser = HtmlParser()

    @Test fun simpleGetFormEncodesOneEditableFieldAndHiddenValues() {
        val page = parser.parse("""
            <form action="/find?old=discarded#section" method="GET">
              <input type="search" name="q" value="old query">
              <input type="hidden" name="lang" value="en">
              <button type="submit" name="go" value="1">Find</button>
            </form>
        """.trimIndent())
        val form = PageForm.find(page, "https://example.com/docs/start")
        assertNotNull(form)
        assertEquals("old query", form!!.initialValue)
        assertEquals("Find", form.submitLabel)
        assertEquals("https://example.com/find?q=C%2B%2B+%26+Kotlin&lang=en&go=1#section", form.submissionUrl("C++ & Kotlin").value)
    }

    @Test fun missingActionAndMethodUseCurrentHttpsPageWithNewQuery() {
        val page = parser.parse("<form><input name='term'><input type='submit' value='Go'></form>")
        val form = PageForm.find(page, "https://example.com/docs/?old=1")!!
        assertEquals("https://example.com/docs/?term=hello+world", form.submissionUrl("hello world").value)
        assertEquals("Go", form.submitLabel)
    }

    @Test fun duckDuckGoLitePostSearchIsAdaptedOnlyOnItsRealOrigin() {
        val page = parser.parse("""
            <form action="/lite/" method="post">
              <input class='query' type="text" name="q" value="android development">
              <input type="submit" value="Search">
              <select name="kl"><option>all</option></select>
            </form>
        """.trimIndent())
        val lite = PageForm.find(page, "https://lite.duckduckgo.com/lite/?q=android+development")!!
        assertEquals("Search again", lite.fieldLabel)
        assertEquals("https://lite.duckduckgo.com/lite/?q=new+topic", lite.submissionUrl("new topic").value)
        assertNull(PageForm.find(page, "https://example.com/lite/"))
        assertNull(PageForm.find(page, "https://lite.duckduckgo.com/other"))
    }

    @Test fun unsupportedFormControlsAndMethodsAreNotOffered() {
        assertNull(PageForm.find(parser.parse("<form method='post'><input name='q'></form>"), "https://example.com/"))
        assertNull(PageForm.find(parser.parse("<form><input name='q'><select name='region'></select></form>"), "https://example.com/"))
        assertNull(PageForm.find(parser.parse("<form><input type='password' name='p'></form>"), "https://example.com/"))
        assertNull(PageForm.find(parser.parse("<form><input name='q'><input type='text' name='other'></form>"), "https://example.com/"))
    }

    @Test fun insecureActionIsRejectedWhenUserSubmits() {
        val form = PageForm.find(parser.parse("<form action='http://example.com/find'><input name='q'></form>"), "https://example.com/")!!
        assertThrows(AddressValidationException::class.java) { form.submissionUrl("query") }
    }
}
