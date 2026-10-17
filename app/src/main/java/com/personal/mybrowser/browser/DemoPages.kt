package com.personal.mybrowser.browser

/** Controlled fixtures for our parser/layout subset; page names are exact-match keys. */
object DemoPages {
    const val HOME = "index.html"
    const val ABOUT = "about.html"
    const val TOO_DEEP = "too-deep.html"
    const val TOO_MANY_NODES = "too-many-nodes.html"

    val pages = mapOf(
        HOME to """
            <h1>MyBrowser</h1>
            <p>Our <b>first</b> page, drawn by our own engine.</p>
            <p><a href="about.html">Read about this browser and how its links work</a></p>
            <h2>Text inside special elements</h2>
            <script>const message = "</p><p>Hidden script text</p> &amp;";</script>
            <style>p::before { content: "</div><b>Hidden style text</b> &amp;"; }</style>
            <title>Hidden &amp; literal <b>title</b></title>
            <p>Visible after script and style.</p>
            <p><textarea>Tom &amp; Jerry: <b>literal text</b></textarea></p>
            <h2>HTML entities</h2>
            <p>Tom &amp; Jerry. 5 &lt; 10. &#65; = &#x41;. Smile: &#x1F600;</p>
            <p>Literal markup: &lt;b&gt;Hello&lt;/b&gt; stays ordinary text.</p>
            <p><a href="about&#46;html">An encoded link to About</a></p>
            <h2 style="color: #1565c0; font-size: 24px;">A little style</h2>
            <p style="color: #1565c0; font-size: 20px;">This blue paragraph has larger text.
            Its <b>bold child</b> inherits the same colour and size.</p>
            <p style="font-size: 20px;">This larger paragraph contains a
            <span style="color: #ad1457; font-size: 14px;">smaller pink phrase</span>
            before returning to its parent's size and the theme's text colour.</p>
            <h2>A page takes shape</h2>
            <p>The parser turned this HTML into a document tree. Our layout pass decides where
            the text goes, and the painter draws the result on the screen.</p>
            <p>This paragraph is long enough to wrap across several lines on a phone. Rotate
            the device and watch the same content reflow to fit the available width.</p>
            <h2>Small details matter</h2>
            <p>Normal text can contain <b>bold words</b> and then return to normal. The words
            share a baseline, even when their font measurements differ.</p>
            <p>Extra     spaces and source-code line breaks collapse into single spaces.
            Paragraphs start new blocks with a small gap between them.</p>
            <h2>Explicit line breaks</h2>
            <p>This is the first line.<br>This is the second line.<br><br>This follows a blank line.</p>
            <h2>Keep scrolling</h2>
            <p>The engine calculates the full page height. Android provides scrolling so we
            can reach the content below the visible part of the screen.</p>
            <p>These headings, paragraphs and bold spans all came from the same local HTML
            string. There is no network request involved in this demo.</p>
            <h2>Complexity checks</h2>
            <p><a href="too-deep.html">Try a deeply nested page</a></p>
            <p><a href="too-many-nodes.html">Try a page with too many nodes</a></p>
            <h2>End of the page</h2>
            <p>You have reached the last paragraph. Try opening a link after scrolling:</p>
            <p><a href="about.html">About our browser</a></p>
        """.trimIndent(),
        ABOUT to """
            <h1>About our browser</h1>
            <p>You followed a link to a second local page.</p>
            <p>A link can contain <a href="index.html"><b>bold text</b> and ordinary text</a>.
            Every rendered part keeps the same destination.</p>
            <h2>Try going back</h2>
            <p>Use Android's Back gesture or button to return to the previous visit.</p>
            <p>Or <a href="index.html">open the first page again</a> as a new visit.</p>
            <h2>Local pages only</h2>
            <p>These demo pages are bundled with the app and work offline.</p>
        """.trimIndent(),
        // Deliberately exceed defaults so the on-device error and Back flow is reproducible.
        TOO_DEEP to "<div>".repeat(129) + "Too deep" + "</div>".repeat(129),
        TOO_MANY_NODES to "<br>".repeat(10_001),
    )
}
