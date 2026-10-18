package com.personal.mybrowser.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class BrowserNavigation(val id: Long, val url: String)

data class BrowserTab(
    val id: Long,
    val url: String? = null,
    val navigationRequest: BrowserNavigation? = null,
)

/** Tabs and their latest addresses live only while the normal browser is open. */
class BrowserTabsState {
    var tabs by mutableStateOf(listOf(BrowserTab(id = 1L)))
        private set
    var selectedId by mutableLongStateOf(1L)
        private set
    private var nextId = 2L
    private var nextNavigationId = 1_000_000L

    val selectedTab: BrowserTab get() = tabs.first { it.id == selectedId }

    fun openTab(url: String? = null): BrowserTab {
        val id = nextId++
        val tab = BrowserTab(
            id = id,
            url = url,
            navigationRequest = url?.let { BrowserNavigation(-id, it) },
        )
        tabs = tabs + tab
        selectedId = tab.id
        return tab
    }

    fun selectTab(id: Long): Boolean {
        if (tabs.none { it.id == id }) return false
        selectedId = id
        return true
    }

    fun closeTab(id: Long): BrowserTab {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return selectedTab
        tabs = tabs.filterNot { it.id == id }
        if (tabs.isEmpty()) {
            val emptyTab = BrowserTab(id = nextId++)
            tabs = listOf(emptyTab)
            selectedId = emptyTab.id
        } else if (selectedId == id) {
            selectedId = tabs[(index - 1).coerceAtLeast(0)].id
        }
        return selectedTab
    }

    fun updateUrl(id: Long, url: String?) {
        tabs = tabs.map { tab -> if (tab.id == id && tab.url != url) tab.copy(url = url) else tab }
    }

    /** Sends an address entered in the shared browser toolbar to the selected WebView. */
    fun navigateSelected(url: String) {
        val request = BrowserNavigation(nextNavigationId++, url)
        tabs = tabs.map { tab ->
            if (tab.id == selectedId) tab.copy(url = url, navigationRequest = request) else tab
        }
    }

    fun route(request: BrowserNavigation) {
        tabs = tabs.map { tab ->
            if (tab.id == selectedId) tab.copy(url = request.url, navigationRequest = request) else tab
        }
    }
}
