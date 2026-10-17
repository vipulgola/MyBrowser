package com.personal.mybrowser.engine.html

/** Semicolon-required subset; decode token values once, never the original markup. */
internal object HtmlEntityDecoder {
    private val reference = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[a-zA-Z][a-zA-Z0-9]*);")
    private val named = mapOf(
        "amp" to "&", "AMP" to "&", "lt" to "<", "LT" to "<",
        "gt" to ">", "GT" to ">", "quot" to "\"", "QUOT" to "\"", "apos" to "'",
        "nbsp" to "\u00a0", "copy" to "©", "reg" to "®", "trade" to "™",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "bull" to "•",
        "laquo" to "«", "raquo" to "»", "euro" to "€", "pound" to "£",
        "yen" to "¥", "cent" to "¢",
    )
    // HTML's legacy numeric-reference replacements, not a general charset conversion.
    private val legacyNumeric = mapOf(
        0x80 to 0x20ac, 0x82 to 0x201a, 0x83 to 0x0192, 0x84 to 0x201e,
        0x85 to 0x2026, 0x86 to 0x2020, 0x87 to 0x2021, 0x88 to 0x02c6,
        0x89 to 0x2030, 0x8a to 0x0160, 0x8b to 0x2039, 0x8c to 0x0152,
        0x8e to 0x017d, 0x91 to 0x2018, 0x92 to 0x2019, 0x93 to 0x201c,
        0x94 to 0x201d, 0x95 to 0x2022, 0x96 to 0x2013, 0x97 to 0x2014,
        0x98 to 0x02dc, 0x99 to 0x2122, 0x9a to 0x0161, 0x9b to 0x203a,
        0x9c to 0x0153, 0x9e to 0x017e, 0x9f to 0x0178,
    )

    fun decode(value: String): String = reference.replace(value) { match ->
        val body = match.groupValues[1]
        if (body.startsWith('#')) decodeNumeric(body) else named[body] ?: match.value
    }

    private fun decodeNumeric(body: String): String {
        val hex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
        val digits = body.substring(if (hex) 2 else 1)
        val number = digits.toIntOrNull(if (hex) 16 else 10)
        // A syntactically valid reference with an invalid scalar (or overflow) becomes U+FFFD.
        if (number == null || number == 0 || number > 0x10ffff || number in 0xd800..0xdfff) {
            return "\ufffd"
        }
        val codePoint = legacyNumeric[number] ?: number
        return String(Character.toChars(codePoint))
    }
}
