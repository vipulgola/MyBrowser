package com.personal.mybrowser.network

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class AddressValidationException(message: String) : IllegalArgumentException(message)

/** A validated HTTPS address. Construction does not resolve DNS or connect to a server. */
class HttpsAddress private constructor(val value: String) {
    // A fragment identifies a location within the document; it is not sent in HTTP.
    val requestUrl: String get() = value.substringBefore('#')

    /** Resolve an href against this document, without address-bar host guessing. */
    fun resolveLink(href: String): HttpsAddress {
        val text = href.trim()
        if (text.any { it.isWhitespace() || Character.isSpaceChar(it) || Character.isISOControl(it) || it == '\\' }) {
            throw AddressValidationException("This link contains invalid characters.")
        }
        val reference = try {
            URI(text)
        } catch (_: URISyntaxException) {
            throw AddressValidationException("This link has an invalid address.")
        }
        if (reference.scheme != null && !reference.scheme.equals("https", ignoreCase = true)) {
            throw AddressValidationException("Only HTTPS links are supported.")
        }
        // An explicit scheme or // must supply a host; do not reinterpret malformed URLs.
        val replacesHost = reference.scheme != null || text.startsWith("//")
        if (replacesHost && (reference.isOpaque || reference.rawAuthority.isNullOrEmpty())) {
            throw AddressValidationException("This link has an invalid HTTPS address.")
        }
        val base = URI(value)
        val path = reference.rawPath
        val targetPath = when {
            replacesHost -> removeDotSegments(path)
            path.isEmpty() -> base.rawPath
            path.startsWith('/') -> removeDotSegments(path)
            else -> removeDotSegments(base.rawPath.substringBeforeLast('/') + "/" + path)
        }
        val query = if (!replacesHost && path.isEmpty()) reference.rawQuery ?: base.rawQuery else reference.rawQuery
        val resolved = buildString {
            append("https://")
            append(if (replacesHost) reference.rawAuthority else base.rawAuthority)
            append(targetPath)
            query?.let { append('?'); append(it) }
            reference.rawFragment?.let { append('#'); append(it) }
        }
        return parseInput(resolved) // Apply the same host, credentials and port policy as Go.
    }

    // RFC 3986 section 5.2: literal dot segments only, preserving escaped and empty segments.
    // All paths here are absolute (or empty); parent traversal stops at the host root.
    private fun removeDotSegments(path: String): String {
        if (path.isEmpty()) return path
        val parts = path.substring(1).split('/')
        val output = mutableListOf<String>()
        parts.forEachIndexed { index, part ->
            when (part) {
                "." -> if (index == parts.lastIndex) output.add("")
                ".." -> {
                    if (output.isNotEmpty()) output.removeAt(output.lastIndex)
                    if (index == parts.lastIndex) output.add("")
                }
                else -> output.add(part)
            }
        }
        return "/" + output.joinToString("/")
    }

    override fun equals(other: Any?) = other is HttpsAddress && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        fun parseInput(input: String): HttpsAddress {
            val text = input.trim()
            if (text.isEmpty()) throw AddressValidationException("Enter a website address.")
            if (text.any { it.isWhitespace() || Character.isSpaceChar(it) || Character.isISOControl(it) || it == '\\' }) {
                throw AddressValidationException("The address contains invalid characters.")
            }
            val scheme = schemePrefix.find(text)?.groupValues?.get(1)
            // example.com:8443 resembles a URI scheme; treat this common host:port input as a host.
            val hostWithPort = hostPort.matches(text)
            val candidate = when {
                text.startsWith("//") -> "https:$text"
                scheme == null || hostWithPort -> "https://$text"
                scheme.equals("https", ignoreCase = true) -> text
                else -> throw AddressValidationException("Only HTTPS addresses are supported.")
            }
            val uri = try {
                URI(candidate).parseServerAuthority()
            } catch (_: URISyntaxException) {
                throw AddressValidationException("Enter a valid HTTPS website address.")
            }
            val host = uri.host
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || host.isNullOrEmpty()) {
                throw AddressValidationException("Enter a valid HTTPS website address.")
            }
            if (uri.rawUserInfo != null) throw AddressValidationException("Addresses containing a username or password are not supported.")
            if (uri.port != -1 && uri.port !in 1..65535 || uri.rawAuthority.endsWith(':')) {
                throw AddressValidationException("Enter a port number between 1 and 65535.")
            }
            if (host.contains('%')) throw AddressValidationException("This hostname format is not supported.")
            val canonicalHost = host.lowercase(Locale.ROOT).let {
                if (':' in it && !it.startsWith('[')) "[$it]" else it
            }
            // Use raw components to preserve escapes such as %2F and %20 without double encoding.
            val canonical = buildString {
                append("https://")
                append(canonicalHost)
                if (uri.port != -1 && uri.port != 443) append(":${uri.port}")
                append(uri.rawPath.ifEmpty { "/" })
                uri.rawQuery?.let { append('?'); append(it) }
                uri.rawFragment?.let { append('#'); append(it) }
            }
            return HttpsAddress(URI(canonical).toASCIIString())
        }

        private val schemePrefix = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
        private val hostPort = Regex("^(?:localhost|[^/:?#]+\\.[^/:?#]+):[0-9]+(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
    }
}
