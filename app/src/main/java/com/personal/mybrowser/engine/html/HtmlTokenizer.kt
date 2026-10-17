package com.personal.mybrowser.engine.html

internal sealed interface HtmlToken {
    data class StartTag(
        val name: String,
        val attributes: Map<String, String>,
        val selfClosing: Boolean,
    ) : HtmlToken

    data class EndTag(val name: String) : HtmlToken
    data class Text(val data: String) : HtmlToken
}

/**
 * Small scanner for our controlled HTML fixtures, not the WHATWG tokenizer.
 * Supports a character-reference subset and basic text-element modes, not full script states.
 */
internal class HtmlTokenizer(private val source: String) {
    fun tokenize(): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val text = StringBuilder()
        var position = 0

        fun flushText() {
            if (text.isNotEmpty()) {
                tokens += HtmlToken.Text(HtmlEntityDecoder.decode(text.toString()))
                text.setLength(0)
            }
        }

        while (position < source.length) {
            if (source.startsWith("<!--", position)) {
                flushText()
                val end = source.indexOf("-->", position + 4)
                position = if (end == -1) source.length else end + 3
                continue
            }

            // Declarations, including DOCTYPE, do not affect this milestone's tree.
            if (source.startsWith("<!", position)) {
                flushText()
                val end = findTagEnd(position + 2)
                position = if (end == -1) source.length else end + 1
                continue
            }

            val closing = source.startsWith("</", position)
            val nameStart = position + if (closing) 2 else 1
            if (source[position] == '<' &&
                nameStart < source.length && source[nameStart].isAsciiLetter()
            ) {
                val end = findTagEnd(nameStart)
                if (end == -1) {
                    // Preserve an unfinished tag as literal text rather than dropping it.
                    text.append(source, position, source.length)
                    break
                }
                flushText()
                val body = source.substring(nameStart, end)
                val token = if (closing) {
                    HtmlToken.EndTag(body.takeWhile { !it.isHtmlSpace() && it != '/' }.asciiLowercase())
                } else {
                    readStartTag(body)
                }
                tokens += token
                position = end + 1
                if (token is HtmlToken.StartTag && token.name in textElements) {
                    // Recognize the closing delimiter in the source BEFORE decoding any entities.
                    // A slash on these non-void start tags does not prevent text mode.
                    val close = findTextElementEnd(position, token.name)
                    val content = source.substring(position, close?.first ?: source.length)
                    if (content.isNotEmpty()) {
                        val value = if (token.name in referenceTextElements) HtmlEntityDecoder.decode(content) else content
                        tokens += HtmlToken.Text(value)
                    }
                    if (close != null) tokens += HtmlToken.EndTag(token.name)
                    position = close?.second ?: source.length
                }
            } else {
                text.append(source[position++])
            }
        }
        flushText()
        return tokens
    }

    /** Returns the closing tag's source start and end-exclusive offsets, or EOF recovery. */
    private fun findTextElementEnd(start: Int, name: String): Pair<Int, Int>? {
        var candidate = source.indexOf("</", start)
        while (candidate != -1) {
            val nameStart = candidate + 2
            val nameEnd = nameStart + name.length
            if (nameEnd < source.length && source.substring(nameStart, nameEnd).asciiLowercase() == name) {
                val delimiter = source[nameEnd]
                if (delimiter == '>' || delimiter == '/' || delimiter.isHtmlSpace()) {
                    val end = findTagEnd(nameEnd)
                    // Preserve an incomplete closing tag and the remaining content as text.
                    return if (end == -1) null else candidate to (end + 1)
                }
            }
            candidate = source.indexOf("</", nameStart)
        }
        return null
    }

    private companion object {
        val referenceTextElements = setOf("title", "textarea")
        val textElements = setOf("script", "style") + referenceTextElements
    }

    // A '>' inside an attribute's quoted value is data, not the end of the tag.
    private fun findTagEnd(start: Int): Int {
        var quote: Char? = null
        for (index in start until source.length) {
            val char = source[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else {
                when (char) {
                    '\'', '"' -> quote = char
                    '>' -> return index
                }
            }
        }
        return -1
    }

    private fun readStartTag(body: String): HtmlToken.StartTag {
        var position = 0
        while (position < body.length && !body[position].isHtmlSpace() && body[position] != '/') {
            position++
        }
        val name = body.substring(0, position).asciiLowercase()
        val attributes = linkedMapOf<String, String>()
        var selfClosing = false

        fun skipSpaces() {
            while (position < body.length && body[position].isHtmlSpace()) position++
        }

        while (position < body.length) {
            skipSpaces()
            if (position == body.length) break
            if (body[position] == '/') {
                position++
                skipSpaces()
                if (position == body.length) selfClosing = true
                continue
            }
            val start = position
            while (position < body.length && !body[position].isHtmlSpace() &&
                body[position] != '=' && body[position] != '/'
            ) {
                position++
            }
            if (position == start) {
                position++ // Always make progress on malformed input, e.g. <p =broken>.
                continue
            }
            val attributeName = body.substring(start, position).asciiLowercase()
            skipSpaces()
            var value = ""
            if (position < body.length && body[position] == '=') {
                position++
                skipSpaces()
                if (position < body.length) {
                    val quote = body[position]
                    if (quote == '\'' || quote == '"') {
                        position++
                        val valueStart = position
                        while (position < body.length && body[position] != quote) position++
                        value = body.substring(valueStart, position)
                        if (position < body.length) position++
                    } else {
                        val valueStart = position
                        while (position < body.length && !body[position].isHtmlSpace()) position++
                        value = body.substring(valueStart, position)
                    }
                }
            }
            // Duplicate attributes keep the first value; boolean attributes have an empty value.
            if (attributeName !in attributes) attributes[attributeName] = HtmlEntityDecoder.decode(value)
        }
        return HtmlToken.StartTag(name, attributes.toMap(), selfClosing)
    }
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
private fun Char.isHtmlSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'
private fun String.asciiLowercase() = map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")
