package com.personal.mybrowser.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class BrowserHistoryEntry(
    val id: Long,
    val url: String,
    val title: String?,
    val visitedAtMillis: Long,
)

/** App-private, persistent visit history. Entries remain until the user removes them. */
class BrowserHistoryRepository(
    context: Context,
    private val preferencesName: String = "browser_history",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    var entries by mutableStateOf(loadEntries())
        private set

    fun record(url: String, title: String?): Boolean {
        if (!CompatibleNavigation.isAllowedNavigation(url)) return false
        val now = System.currentTimeMillis()
        val readableTitle = title?.trim()?.takeIf(String::isNotEmpty)
        val newest = entries.firstOrNull()
        val updated = if (newest?.url == url) {
            listOf(newest.copy(title = readableTitle ?: newest.title, visitedAtMillis = now)) + entries.drop(1)
        } else {
            listOf(BrowserHistoryEntry(nextId(entries, now), url, readableTitle, now)) + entries
        }
        return save(updated).also { if (it) entries = updated }
    }

    fun remove(id: Long): Boolean {
        val updated = entries.filterNot { it.id == id }
        if (updated.size == entries.size) return true
        return save(updated).also { if (it) entries = updated }
    }

    fun clear(): Boolean = save(emptyList()).also { if (it) entries = emptyList() }

    private fun loadEntries(): List<BrowserHistoryEntry> {
        val raw = preferences.getString(ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", 0L)
                    val url = item.optString("url")
                    if (id <= 0L || !CompatibleNavigation.isAllowedNavigation(url)) continue
                    add(
                        BrowserHistoryEntry(
                            id = id,
                            url = url,
                            title = item.optString("title").takeIf(String::isNotEmpty),
                            visitedAtMillis = item.optLong("visitedAt", 0L),
                        ),
                    )
                }
            }.sortedByDescending(BrowserHistoryEntry::visitedAtMillis)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(updated: List<BrowserHistoryEntry>): Boolean {
        val array = JSONArray()
        updated.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("url", entry.url)
                    put("title", entry.title.orEmpty())
                    put("visitedAt", entry.visitedAtMillis)
                },
            )
        }
        return preferences.edit().putString(ENTRIES, array.toString()).commit()
    }

    private fun nextId(current: List<BrowserHistoryEntry>, now: Long): Long =
        maxOf(now, (current.maxOfOrNull(BrowserHistoryEntry::id) ?: 0L) + 1L)

    private companion object {
        const val ENTRIES = "entries"
    }
}
