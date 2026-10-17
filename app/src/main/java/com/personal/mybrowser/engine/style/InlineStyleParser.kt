package com.personal.mybrowser.engine.style

internal data class InlineStyle(val fontSizeCssPx: Float? = null, val colorArgb: Int? = null)

/** Two-property subset. Unsupported/invalid declarations do not erase earlier valid ones. */
internal object InlineStyleParser {
    fun parse(source: String): InlineStyle {
        var result = InlineStyle()
        for (declaration in declarations(source)) {
            val colon = declaration.indexOf(':')
            if (colon == -1) continue
            val property = declaration.substring(0, colon).trim().lowercase()
            val value = declaration.substring(colon + 1).trim()
            when (property) {
                "font-size" -> parseFontSize(value)?.let { result = result.copy(fontSizeCssPx = it) }
                "color" -> parseColor(value)?.let { result = result.copy(colorArgb = it) }
            }
        }
        return result
    }

    private fun parseFontSize(value: String): Float? {
        // Decimal px lengths plus unitless zero. Keywords, functions and other units wait.
        if (value == "0") return 0f
        val match = fontSize.matchEntire(value) ?: return null
        return match.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    }

    private fun parseColor(value: String): Int? {
        if (!value.startsWith('#')) return null
        val hex = value.drop(1)
        if (hex.length !in setOf(3, 4, 6, 8) || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val expanded = if (hex.length <= 4) hex.flatMap { listOf(it, it) }.joinToString("") else hex
        val rgb = expanded.take(6).toLong(16).toInt()
        // CSS puts alpha LAST (#RRGGBBAA); Android's packed color puts alpha FIRST.
        val alpha = if (expanded.length == 8) expanded.takeLast(2).toInt(16) else 255
        return (alpha shl 24) or rgb
    }

    private val fontSize = Regex("([+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))px", RegexOption.IGNORE_CASE)

    /**
     * Split only at top-level semicolons. This prevents a quoted/functional value in an
     * unsupported property from being mistaken for a new supported declaration.
     * This is still not a full CSS tokenizer (escapes in property names, etc. are absent).
     */
    private fun declarations(source: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var position = 0
        var quote: Char? = null
        var parentheses = 0
        while (position < source.length) {
            val char = source[position]
            if (quote != null) {
                current.append(char)
                if (char == '\\' && position + 1 < source.length) current.append(source[++position])
                else if (char == quote) quote = null
            } else if (source.startsWith("/*", position)) {
                val end = source.indexOf("*/", position + 2)
                current.append(' ')
                position = if (end == -1) source.length else end + 2
                continue
            } else {
                when (char) {
                    '\'', '"' -> { quote = char; current.append(char) }
                    '(' -> { parentheses++; current.append(char) }
                    ')' -> { parentheses = (parentheses - 1).coerceAtLeast(0); current.append(char) }
                    ';' -> if (parentheses == 0) {
                        result += current.toString()
                        current.setLength(0)
                    } else current.append(char)
                    else -> current.append(char)
                }
            }
            position++
        }
        result += current.toString()
        return result
    }
}
