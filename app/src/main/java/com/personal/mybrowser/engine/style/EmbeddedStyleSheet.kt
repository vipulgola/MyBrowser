package com.personal.mybrowser.engine.style

import com.personal.mybrowser.engine.html.HtmlNode

/** A deliberately small, bounded subset of author CSS from HTML <style> elements. */
internal object EmbeddedStyleSheet {
    private const val MAX_CSS_CHARS = 16_384
    private const val MAX_RULES = 128

    data class Rule(
        val tagName: String?,
        val className: String?,
        val declarations: InlineStyle,
        val order: Int,
    ) {
        val specificity: Int = (if (className != null) 10 else 0) + (if (tagName != null) 1 else 0)

        fun matches(element: HtmlNode.Element, classes: Set<String>): Boolean =
            (tagName == null || tagName == element.tagName) && (className == null || className in classes)
    }

    fun parse(document: HtmlNode.Document): List<Rule> {
        val rules = mutableListOf<Rule>()
        var remainingChars = MAX_CSS_CHARS

        fun visit(node: HtmlNode) {
            if (remainingChars == 0 || rules.size == MAX_RULES) return
            when (node) {
                is HtmlNode.Document -> node.children.forEach(::visit)
                is HtmlNode.Element -> {
                    if (node.tagName == "style" && node.attributes["type"]?.trim()?.lowercase() in setOf(null, "", "text/css")) {
                        val css = buildString {
                            for (child in node.children) {
                                if (child is HtmlNode.Text && length < remainingChars) {
                                    append(child.data.take(remainingChars - length))
                                }
                            }
                        }
                        remainingChars -= css.length
                        parseBlock(css, rules)
                    } else node.children.forEach(::visit)
                }
                is HtmlNode.Text -> Unit
            }
        }

        visit(document)
        return rules.sortedWith(compareBy<Rule> { it.specificity }.thenBy { it.order })
    }

    private fun parseBlock(css: String, rules: MutableList<Rule>) {
        var position = 0
        while (position < css.length && rules.size < MAX_RULES) {
            val prelude = scanUntil(css, position, setOf('{', ';', '}'))
            position = prelude.next
            if (prelude.delimiter != '{') continue
            val body = scanBody(css, position) ?: break
            position = body.next
            if (body.hasNestedBlock || prelude.text.trimStart().startsWith('@')) continue
            val declarations = InlineStyleParser.parse(body.text)
            if (declarations == InlineStyle()) continue
            for (selector in prelude.text.split(',')) {
                if (rules.size == MAX_RULES) break
                val match = simpleSelector.matchEntire(selector.trim()) ?: continue
                val tagName = match.groupValues[1].takeIf { it.isNotEmpty() }?.lowercase()
                val className = match.groupValues[2].takeIf { it.isNotEmpty() }
                if (tagName == null && className == null) continue
                rules += Rule(tagName, className, declarations, rules.size)
            }
        }
    }

    private data class Scan(val text: String, val delimiter: Char?, val next: Int)

    /** Ignore comments and quoted delimiters while looking for a rule boundary. */
    private fun scanUntil(source: String, start: Int, delimiters: Set<Char>): Scan {
        val text = StringBuilder()
        var position = start
        var quote: Char? = null
        var parentheses = 0
        while (position < source.length) {
            val char = source[position]
            if (quote != null) {
                text.append(char)
                if (char == '\\' && position + 1 < source.length) text.append(source[++position])
                else if (char == quote) quote = null
            } else if (source.startsWith("/*", position)) {
                val end = source.indexOf("*/", position + 2)
                if (end == -1) return Scan(text.toString(), null, source.length)
                text.append(' ')
                position = end + 2
                continue
            } else {
                when (char) {
                    '\'', '"' -> { quote = char; text.append(char) }
                    '(' -> { parentheses++; text.append(char) }
                    ')' -> { parentheses = (parentheses - 1).coerceAtLeast(0); text.append(char) }
                    in delimiters -> if (parentheses == 0) return Scan(text.toString(), char, position + 1)
                        else text.append(char)
                    else -> text.append(char)
                }
            }
            position++
        }
        return Scan(text.toString(), null, position)
    }

    private data class Body(val text: String, val next: Int, val hasNestedBlock: Boolean)

    private fun scanBody(source: String, start: Int): Body? {
        val text = StringBuilder()
        var position = start
        var depth = 1
        var nested = false
        var quote: Char? = null
        while (position < source.length) {
            val char = source[position]
            if (quote != null) {
                text.append(char)
                if (char == '\\' && position + 1 < source.length) text.append(source[++position])
                else if (char == quote) quote = null
            } else if (source.startsWith("/*", position)) {
                val end = source.indexOf("*/", position + 2)
                if (end == -1) return null
                text.append(' ')
                position = end + 2
                continue
            } else {
                when (char) {
                    '\'', '"' -> { quote = char; text.append(char) }
                    '{' -> { depth++; nested = true; text.append(char) }
                    '}' -> {
                        depth--
                        if (depth == 0) return Body(text.toString(), position + 1, nested)
                        text.append(char)
                    }
                    else -> text.append(char)
                }
            }
            position++
        }
        return null
    }

    // One tag, one class, or a tag and a class. No combinators, IDs or pseudo-classes.
    private val simpleSelector = Regex("([A-Za-z][A-Za-z0-9-]*)?(?:\\.([A-Za-z_][A-Za-z0-9_-]*))?")
}
