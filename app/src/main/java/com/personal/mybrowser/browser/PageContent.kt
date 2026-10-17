package com.personal.mybrowser.browser

import com.personal.mybrowser.engine.html.HtmlComplexityException
import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.html.HtmlParser

/** Rendering boundary: rejected documents must never reach recursive styling or layout. */
sealed interface PageContent {
    data class Ready(val document: HtmlNode.Document) : PageContent
    data object TooComplex : PageContent

    companion object {
        fun parse(html: String): PageContent = try {
            Ready(HtmlParser().parse(html))
        } catch (_: HtmlComplexityException) {
            TooComplex
        }
    }
}
