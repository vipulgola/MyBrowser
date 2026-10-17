package com.personal.mybrowser.engine.html

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlParserTest {
    private val parser = HtmlParser()

    @Test fun buildsTheMilestoneExample() {
        assertEquals(
            HtmlNode.Document(mutableListOf(
                element("h1", text("Hello")),
                element("p", text("Our "), element("b", text("first")), text(" page.")),
            )),
            parser.parse("<h1>Hello</h1><p>Our <b>first</b> page.</p>"),
        )
    }

    @Test fun attachesAttributesToTheirElement() {
        val link = parser.parse("<a href='next.html' title='Read more'>Next</a>").children.single() as HtmlNode.Element
        assertEquals(mapOf("href" to "next.html", "title" to "Read more"), link.attributes)
        assertEquals(listOf(text("Next")), link.children)
    }

    @Test fun voidElementsNeverCaptureFollowingText() {
        assertEquals(
            listOf(element("p", text("one"), element("br"), text("two"), element("img"), text("three"))),
            parser.parse("<p>one<br>two<img/>three</p>").children,
        )
    }

    @Test fun matchesNamesRegardlessOfAsciiCase() {
        assertEquals(listOf(element("p", text("Hi")), text("after")), parser.parse("<P>Hi</p>after").children)
    }

    @Test fun mismatchedEndTagClosesThroughMatchingAncestor() {
        assertEquals(
            listOf(element("div", element("b", text("inside"))), text("outside")),
            parser.parse("<div><b>inside</div>outside").children,
        )
    }

    @Test fun unmatchedEndTagDoesNotPopCurrentElement() {
        assertEquals(listOf(element("p", text("before"), text("after"))), parser.parse("<p>before</aside>after</p>").children)
    }

    @Test fun missingClosingTagsKeepTheirChildren() {
        assertEquals(listOf(element("div", element("p", text("still here")))), parser.parse("<div><p>still here").children)
    }

    @Test fun slashDoesNotCloseAnOrdinaryHtmlElement() {
        assertEquals(listOf(element("div", text("inside")), text("outside")), parser.parse("<div/>inside</div>outside").children)
    }

    @Test fun repeatedNamesCloseTheNearestOpenElement() {
        assertEquals(listOf(element("div", element("div", text("inner")), text("outer"))), parser.parse("<div><div>inner</div>outer</div>").children)
    }

    @Test fun preservesWhitespaceBetweenSiblings() {
        assertEquals(listOf(element("b", text("one")), text(" \n "), element("b", text("two"))), parser.parse("<b>one</b> \n <b>two</b>").children)
    }

    @Test fun emptyDocumentAndParserReuseHaveNoSharedState() {
        parser.parse("<div>previous")
        assertEquals(HtmlNode.Document(), parser.parse(""))
        assertEquals(listOf(text("new")), parser.parse("new").children)
    }

    private fun text(value: String) = HtmlNode.Text(value)
    private fun element(name: String, vararg children: HtmlNode) =
        HtmlNode.Element(name, children = children.toMutableList())
}
