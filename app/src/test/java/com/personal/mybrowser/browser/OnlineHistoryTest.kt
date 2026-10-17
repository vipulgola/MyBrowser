package com.personal.mybrowser.browser

import com.personal.mybrowser.network.LoadedHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineHistoryTest {
    private fun page(name: String) = LoadedHtml("https://example.com/$name", "<p>$name</p>")

    @Test fun emptyHistoryHasNoCurrentPageAndBackDoesNothing() {
        val history = OnlineHistory()
        assertEquals(0, history.size)
        assertEquals(-1, history.currentIndex)
        assertEquals(null, history.currentPage)
        assertFalse(history.canGoBack)
        assertSame(history, history.back())
    }

    @Test fun visitsAndBackLeavePreviousHistoryValuesUnchanged() {
        val first = OnlineHistory().visit(page("a"))
        val second = first.visit(page("b"))
        val third = second.visit(page("c"))
        assertEquals(page("a"), first.currentPage)
        assertEquals(page("b"), second.currentPage)
        assertEquals(page("c"), third.currentPage)
        assertEquals(2, third.currentIndex)
        val back = third.back()
        assertEquals(3, back.size) // Forward visits are retained until a new visit commits.
        assertEquals(page("b"), back.currentPage)
        assertEquals(page("a"), back.back().currentPage)
        assertFalse(back.back().canGoBack)
        assertTrue(third.canGoBack)
    }

    @Test fun newVisitAfterBackReplacesTheForwardBranch() {
        val history = OnlineHistory().visit(page("a")).visit(page("b")).visit(page("c"))
        val branched = history.back().visit(page("d"))
        assertEquals(3, branched.size)
        assertEquals(page("d"), branched.currentPage)
        assertEquals(page("b"), branched.back().currentPage)
        assertEquals(page("a"), branched.back().back().currentPage)
    }

    @Test fun repeatedUrlsRetainTheirOwnHtmlSnapshotsAndVisitPositions() {
        val original = page("same")
        val updated = original.copy(html = "<p>updated</p>")
        val history = OnlineHistory().visit(original).visit(updated).visit(updated)
        assertEquals(3, history.size)
        assertEquals(updated, history.back().currentPage)
        assertEquals(original, history.back().back().currentPage)
    }

    @Test fun defaultLimitEvictsOldestVisitAndKeepsTwentySnapshots() {
        var history = OnlineHistory()
        for (number in 0..20) history = history.visit(page("$number"))
        assertEquals(20, history.size)
        assertEquals(19, history.currentIndex)
        for (number in 20 downTo 1) {
            assertEquals(page("$number"), history.currentPage)
            history = history.back()
        }
        assertEquals(page("1"), history.currentPage)
        assertFalse(history.canGoBack)
    }

    @Test fun forwardBranchIsDiscardedBeforeApplyingTheLimit() {
        val history = OnlineHistory(maxEntries = 3).visit(page("a")).visit(page("b")).visit(page("c"))
        val branched = history.back().back().visit(page("d"))
        assertEquals(2, branched.size)
        assertEquals(page("a"), branched.back().currentPage)
    }

    @Test fun singleEntryLimitAlwaysKeepsTheLatestVisit() {
        val history = OnlineHistory(maxEntries = 1).visit(page("a")).visit(page("b"))
        assertEquals(1, history.size)
        assertEquals(0, history.currentIndex)
        assertEquals(page("b"), history.currentPage)
        assertFalse(history.canGoBack)
    }

    @Test fun nonPositiveLimitsAreRejected() {
        for (limit in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) { OnlineHistory(limit) }
        }
    }
}
