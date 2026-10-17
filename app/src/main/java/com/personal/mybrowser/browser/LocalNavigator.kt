package com.personal.mybrowser.browser

/** Immutable, in-memory visits to an explicit collection of bundled pages. */
class LocalNavigator private constructor(
    private val pages: Map<String, String>,
    private val history: List<String>,
    private val currentIndex: Int,
) {
    constructor(pages: Map<String, String>, startPath: String) : this(pages.toMap(), listOf(startPath), 0) {
        require(startPath in pages) { "The initial page must exist" }
    }

    val currentPath: String get() = history[currentIndex]
    val currentHtml: String get() = pages.getValue(currentPath)
    val canGoBack: Boolean get() = currentIndex > 0

    val canGoForward: Boolean get() = currentIndex < history.lastIndex

    /** Unknown targets return the same state. There is no network or external-app fallback. */
    fun navigate(target: String): LocalNavigator =
        if (target in pages) LocalNavigator(pages, history.take(currentIndex + 1) + target, currentIndex + 1) else this

    fun back(): LocalNavigator =
        if (canGoBack) LocalNavigator(pages, history, currentIndex - 1) else this
    fun forward(): LocalNavigator =
        if (canGoForward) LocalNavigator(pages, history, currentIndex + 1) else this

    fun reload(): LocalNavigator = LocalNavigator(pages, history, currentIndex)
}
