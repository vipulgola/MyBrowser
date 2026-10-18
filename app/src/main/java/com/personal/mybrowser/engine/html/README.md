# HTML engine: tree building, text modes and complexity limits

This package is pure Kotlin. It has no Android, WebView or GeckoView dependencies.
The app renders this tree through the style, layout and Canvas stages.

```kotlin
val document = HtmlParser().parse("<h1>Hello</h1><p>Our <b>first</b> page.</p>")
```

## Data flow

`String -> HtmlTokenizer -> List<HtmlToken> -> HtmlParser -> HtmlNode.Document`

The tokenizer recognizes markup and attributes. A quote-aware scan prevents `>`
inside a quoted attribute from ending a tag. Names use ASCII lowercase; values and
text keep their original case and whitespace, with supported references decoded. Text whitespace belongs to the later
layout stage, which will decide when to collapse it.

The parser appends each node to the current open element (or document). A normal
start tag pushes its element on the stack. An end tag pops through the nearest
matching element. Void elements such as `br` and `img` are never pushed. A slash
in `<div/>` does not make that ordinary HTML element void.

Nodes contain structure only, with mutable child lists to support tree construction.
There are no parent pointers or DOM scripting APIs yet. Parse calls do not share state.

## Supported subset and deliberate recovery rules

- Explicitly nested tags, multiple top-level nodes and text.
- Single/double-quoted, unquoted and boolean attributes; first duplicate wins.
- Void HTML elements; comments and declarations are skipped.
- Unmatched closing tags are ignored; unclosed elements remain in the tree at EOF.
- An unfinished tag is retained as literal text. An unfinished comment/declaration
  consumes the remainder. These are our simple recovery rules, not full HTML rules.

## Not supported yet

The full named-reference table and missing-semicolon recovery rules are not implemented.
No complete script escaped/double-escaped states, optional end tags, implicit
html/head/body insertion, table recovery, formatting reconstruction, SVG/MathML,
encoding detection, general parsing diagnostics or streaming. Element-depth and DOM-node
limits are enforced as described below; other resource budgets remain unimplemented.
Use small controlled fixtures only; this is not a standards-compliant parser,
HTML sanitizer, or a security boundary for untrusted web pages.
This package handles parsing only; styling, layout, rendering and networking live in separate packages. Script execution is not implemented.

## Character-reference decoding (milestone 10)

HtmlEntityDecoder is pure Kotlin with no added dependency. The tokenizer first
finds text/tag/attribute boundaries, then decodes text and attribute values once.
Decoded characters are never fed back into the markup scanner. Tag and attribute
names are not decoded. The parser receives the decoded values directly.

- Supported, case-sensitive names: amp/AMP, lt/LT, gt/GT, quot/QUOT, apos,
  nbsp, copy, reg, trade, hellip, ndash, mdash, bull, laquo, raquo, euro, pound,
  yen and cent. Unknown names remain literal.
- Decimal (&#65;) and hexadecimal (&#x41; or &#X41;) references support Unicode
  including supplementary code points such as emoji. All references require a
  trailing semicolon in this deliberate subset. Malformed syntax is preserved.
- Zero, surrogates, out-of-range numbers and integer overflow produce U+FFFD.
  HTML's legacy numeric replacements are applied (for example &#128; becomes €).
  Other valid code points, including noncharacters and unmapped controls, are
  preserved; parse diagnostics are not implemented.
- &amp;lt; becomes &lt;, not <. &lt;b&gt;Hello&lt;/b&gt; remains literal text,
  not an element. Encoded quotes or spaces cannot end/create attributes.
- href="/?a=1&amp;b=2" becomes /?a=1&b=2 before link resolution. URL policy
  validation still happens separately when following the link.
- Basic script/style literal text and title/textarea reference-decoding modes are
  described below. Full HTML tokenizer conformance is not implemented.

Verify these cases with `HtmlEntityDecoderTest` and `HtmlTokenizerTest`. An encoded
`<b>` remains literal text, while an encoded ampersand in an `href` is decoded
before link resolution.

Numeric recovery reference: [WHATWG numeric character reference end state](https://html.spec.whatwg.org/multipage/parsing.html#numeric-character-reference-end-state).

## Text-element modes (milestone 11)

After emitting a script, style, title or textarea start tag, the tokenizer consumes
its source content as one text token until a matching closing tag. This is a
scoped tokenizer mode, not JavaScript or CSS parsing.

- Script/style content is preserved literally, including ampersand references,
  comments, declarations, quotes and apparent start/end tags of other elements.
- Title/textarea content also stays text, but supported character references are
  decoded once after the closing boundary is found. Encoded </textarea> text cannot
  end the element. Apparent <b> or <a> tags do not create DOM children.
- Closing names match ASCII case-insensitively. The name must be followed by HTML
  whitespace, / or >; </scriptx> is ordinary content. A complete close uses the
  existing quote-aware tag-end scanner. An actual </script> inside a JavaScript
  string still ends this basic mode; quotes in the content do not protect it.
- Self-closing syntax on these non-void elements does not bypass text mode.
  Without a complete matching close, the rest of the source is retained as content.
  After the close, ordinary tokenization and entity decoding resume.
- The renderer already hides script, style and title. Textarea displays literal
  text with our ordinary text layout; it is not an editable form control. There is
  no script execution or stylesheet application in this milestone.
- This is not the complete WHATWG script-data state machine. Legacy escaped and
  double-escaped script states, null/newline preprocessing, textarea initial-LF
  removal, and additional raw-text elements are not implemented. Existing simplified
  tag/attribute recovery and tree-building limitations still apply.

Verify text-element handling with `HtmlTokenizerTest` and `TextElementTest`.
Script, style and title content stays hidden, while textarea content is literal text.

Reference: [WHATWG tokenizer states](https://html.spec.whatwg.org/multipage/parsing.html#tokenization).

## Complexity limits (milestone 12)

HtmlParser defaults to at most 128 nested elements and 10,000 DOM nodes. Both
limits are inclusive: the next element/node is rejected with a typed
HtmlComplexityException. Counts are local to a parse call, so parser reuse after
failure starts fresh. Positive constructor overrides support focused tests.

- Element depth includes void and hidden elements. A void element consumes a
  level but does not remain on the open-element stack. Text leaves do not add to
  element depth. Normal closing/recovery rules release nesting depth.
- Node count includes every element and text node, including whitespace text and
  hidden content. It excludes the document container, comments and closing tags.
  Script/style contents remain one text node regardless of apparent tags inside.
- The parser rejects excessive depth before constructing that element and rejects
  excessive node count before appending. No partial DOM is returned to callers.
- Browser PageContent.parse catches only HtmlComplexityException and returns a
  TooComplex result. The UI shows "This page is too complex for this browser yet."
  and a Go back button. It never passes that rejected document to HtmlPageView,
  StyleResolver or HtmlLayoutEngine. Other unexpected exceptions are not masked.
- Pages opened in the experimental engine use this rendering boundary. HTTP success
  still records the URL/HTML visit; a rendering rejection displays an error for that
  visit. Back restores the prior visit (or Home for the first online visit).
  The address field remains available to navigate elsewhere.
- These limits bound DOM structure, not total heap or CPU usage. The tokenizer
  still materializes tokens before tree construction; network HTML retains its
  separate 128 KiB body limit. Layout/font dimensions, token/attribute budgets,
  parse-time deadlines and a complete hostile-content isolation model remain
  future work. The engine is not a full security boundary.

Verify the limits with `HtmlParserLimitsTest` and the browser's
`PageContentTest`. A rejected page shows the readable error in the experimental
engine; Go back or Android Back returns to the prior visit or Home.

## Verification

Run `./gradlew :app:testDebugUnitTest --tests 'com.personal.mybrowser.engine.html.*'`.
Start reading with `HtmlParserTest.buildsTheMilestoneExample`, then step through
`HtmlParser.parse` with the debugger to watch the open-element stack change.

Reference: [WHATWG parsing model](https://html.spec.whatwg.org/multipage/parsing.html#overview-of-the-parsing-model).
The standard also separates tokenization from tree construction, but defines many
more states and recovery rules than this learning implementation.
