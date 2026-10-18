package com.personal.mybrowser.browser

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Host-based request rules. A matching host also blocks its subdomains. */
class AdBlockRules private constructor(private val hosts: Set<String>) {
    val size: Int get() = hosts.size

    fun blocks(url: String, isMainFrame: Boolean): Boolean {
        if (isMainFrame) return false // Keep addresses deliberately opened by the user reachable.
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        if (!"http".equals(uri.scheme, ignoreCase = true) &&
            !"https".equals(uri.scheme, ignoreCase = true)) return false
        var host = uri.host?.lowercase(Locale.ROOT) ?: return false
        while (true) {
            if (host in hosts) return true
            val dot = host.indexOf('.')
            if (dot < 0) return false
            host = host.substring(dot + 1)
        }
    }

    companion object {
        private val whitespace = Regex("\\s+")
        private val validHost = Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")

        /** Peter Lowe's published hosts format; malformed or other rule types are ignored. */
        fun parse(lines: Sequence<String>): AdBlockRules {
            val hosts = HashSet<String>()
            for (line in lines) {
                val text = line.trim()
                if (text.isEmpty() || text.startsWith('#')) continue
                val parts = text.split(whitespace)
                if (parts.size < 2 || parts[0] != "127.0.0.1" && parts[0] != "0.0.0.0") continue
                val host = parts[1].lowercase(Locale.ROOT)
                if (host.length <= 253 && validHost.matches(host)) hosts.add(host)
            }
            return AdBlockRules(hosts)
        }
    }
}
