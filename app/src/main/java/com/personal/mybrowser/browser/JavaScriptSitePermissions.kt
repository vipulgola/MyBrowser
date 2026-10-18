package com.personal.mybrowser.browser

import android.content.Context

/** Saved JavaScript blocks keyed by the validated HTTPS hostname. */
class JavaScriptSitePermissions(
    context: Context,
    preferencesName: String = "javascript_site_permissions",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun isAllowed(url: String?): Boolean {
        val host = SiteIdentity.hostname(url) ?: return false
        return host !in preferences.getStringSet(BLOCKED_HOSTS, emptySet()).orEmpty()
    }

    fun setAllowed(url: String?, allowed: Boolean): Boolean {
        val host = SiteIdentity.hostname(url) ?: return false
        val hosts = preferences.getStringSet(BLOCKED_HOSTS, emptySet()).orEmpty().toMutableSet()
        if (allowed) hosts.remove(host) else hosts.add(host)
        // Save the choice before the page is reloaded with the new setting.
        return preferences.edit().putStringSet(BLOCKED_HOSTS, hosts).commit()
    }

    private companion object {
        const val BLOCKED_HOSTS = "blocked_hosts"
    }
}
