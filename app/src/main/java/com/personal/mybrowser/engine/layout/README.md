# Milestone 2: Document tree to pixels

`HtmlNode.Document -> StyleResolver -> StyledNode -> HtmlLayoutEngine -> PageLayout -> HtmlPageView.onDraw`

`PageLayout` is a display list of `DrawText` instructions and a total content height.
Each instruction carries text, an x coordinate, a baseline y coordinate and a font
style. All engine coordinates use pixels; the Android adapter converts the base
font from sp and page padding from dp before asking for layout.

The engine depends on a `TextMeasurer` interface, not Android. JVM tests use
predictable synthetic font metrics; `AndroidTextMeasurer` uses Paint on the device.
Measurement and painting use the same cached Paint settings.

## Layout mechanics

- The tree walk consumes styles resolved beforehand by StyleResolver. Tag defaults
  and supported inline declarations are described in the style package's README.
- Paragraphs and the supported containers start new blocks. Headings/paragraphs
  add a fixed bottom gap, without CSS margin collapsing.
- ASCII whitespace collapses between words, including across inline elements.
  Leading/trailing spaces are discarded; `br` ends a line and can reserve empty lines.
- A word may have multiple styled runs (`hel<b>lo</b>`), so it is collected before
  deciding whether it fits. Indivisible oversized words overflow and are clipped
  by the view; there is no hyphenation or emergency word breaking yet.
- Lines are collected before their baseline is assigned. Maximum ascent and
  descent keep each run aligned and reserve vertical space. A small leading gap
  separates consecutive lines.
- Layout is recomputed when the document reference or measured width changes.
  The tree is read only during layout. Do not mutate the displayed tree in place;
  supply a new tree to `HtmlPageView.show` when changing content.

## Android integration

The existing Compose Scaffold hosts a ScrollView via AndroidView. HtmlPageView
reports the computed content height during onMeasure, and the ScrollView handles
scroll gestures. onDraw only paints the display list; parsing and layout do not
run on every redraw. Material theme colors supply the text and page background.

There is a single content description for basic read-aloud access.
Per-element accessibility, text selection and full CSS are future work. Milestone
16 added bounded image boxes and same-origin image loading. Text is measured/painted per run with simple whitespace wrapping;
full bidirectional layout, cross-run shaping and Unicode line breaking are not
implemented. Use controlled test fixtures when inspecting this renderer.

## Milestone 3: Links and navigation

Anchor destinations are inherited through inline children and retained separately
from font style. Runs with different destinations must never merge, even if their
fonts match. Each DrawText now includes its measured TextBounds and optional
linkTarget. PageLayout.linkAt searches these bounds in document coordinates.
A wrapping link has multiple clickable fragments; inter-word spaces inside the
anchor are clickable too. Line gaps and surrounding unlinked text are not.

HtmlPageView draws links with the theme's primary color and an underline.
GestureDetector recognizes taps; ScrollView cancels the child gesture when it
intercepts a drag. Events received by the child are already in document-local
coordinates, so adding ScrollView.scrollY would apply the offset twice. TalkBack
custom actions provide another way to open the page's distinct destinations;
full virtual-node accessibility is still future work.

Navigation for the experimental engine lives in the browser package. Link targets
are resolved against the current HTTPS page, validated by `HttpsAddress`, and
stored in `BrowserViewModel`'s in-memory visit history. Back restores the prior
visit or Home; rotation retains the session through the ViewModel. Scroll
restoration is not implemented yet. A new process starts a new session.

Run the tests/build, open a simple HTTPS page in Experimental engine, and tap a
same-site link. Check Back, drag-to-scroll without opening a link, and rotation.

## Verify and inspect

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
Launch the app, open a simple HTTPS page in Experimental engine, and rotate the
emulator to see the document reflow at a new width.

For the engine, set a breakpoint in `flushWord` and inspect `word`, `lineWidth`
and `availableWidth`. Then inspect `baseline` in `flushLine`. Finally break in
`HtmlPageView.onDraw` to see those coordinates passed to Canvas.drawText.

References: [Custom drawing](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing),
[Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose).
