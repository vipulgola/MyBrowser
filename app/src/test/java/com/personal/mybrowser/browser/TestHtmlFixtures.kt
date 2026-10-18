package com.personal.mybrowser.browser

/** Parser limits used by tests only; these pages are not packaged in the app. */
object TestHtmlFixtures {
    val tooDeep = "<div>".repeat(129) + "Too deep" + "</div>".repeat(129)
    val tooManyNodes = "<br>".repeat(10_001)
}
