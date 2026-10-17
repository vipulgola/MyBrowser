package com.personal.mybrowser.browser

import com.personal.mybrowser.network.LoadedHtml

/** Immutable, bounded visits containing URL/HTML snapshots only; never persisted to disk. */
class OnlineHistory private constructor(
    private val entries: List<LoadedHtml>,
    val currentIndex: Int,
    private val maxEntries: Int,
) {
    constructor(maxEntries: Int = DEFAULT_MAX_ENTRIES) : this(emptyList(), -1, maxEntries) {
        require(maxEntries > 0) { "History must retain at least one visit" }
    }

    val size: Int get() = entries.size
    val currentPage: LoadedHtml? get() = entries.getOrNull(currentIndex)
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex >= 0 && currentIndex < entries.lastIndex

    /** Call only for a successful navigation. Repeated URLs remain distinct visits. */
    fun visit(page: LoadedHtml): OnlineHistory {
        // A new successful visit replaces the forward branch, then evicts oldest entries.
        val retained = (entries.take(currentIndex + 1) + page).takeLast(maxEntries)
        return OnlineHistory(retained, retained.lastIndex, maxEntries)
    }

    fun back(): OnlineHistory =
        if (canGoBack) OnlineHistory(entries, currentIndex - 1, maxEntries) else this

    fun forward(): OnlineHistory =
        if (canGoForward) OnlineHistory(entries, currentIndex + 1, maxEntries) else this

    /** Reload updates this visit without removing its forward branch. */
    fun replaceCurrent(page: LoadedHtml): OnlineHistory {
        check(currentIndex >= 0) { "Cannot reload an empty history" }
        return OnlineHistory(entries.mapIndexed { index, entry -> if (index == currentIndex) page else entry }, currentIndex, maxEntries)
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 20
    }
}
