# Resolve styles before layout

`HtmlNode.Document -> StyleResolver -> StyledNode -> HtmlLayoutEngine -> PageLayout -> Canvas`

The style resolver returns a separate tree that references the source nodes. It
does not modify the DOM. Only text properties inherit: size, bold and colour.
Display, bottom spacing and line breaks are resolved for each element independently.
The fixed defaults for headings, blocks, metadata and bold tags now live here.
Layout consumes computed styles and owns positions/wrapping rather than tag styling.

For our subset, resolution starts with inherited text properties, applies element
defaults, then supported rules from `<style>` elements, then valid inline
declarations. Heading default sizes scale the inherited font. A link's default
theme colour overrides inherited text colour; a matching CSS rule or inline colour
on the link can override that default. Bold children keep their parent's colour
and size. Following siblings return to their parent's style.

## Supported inline CSS

```html
<p style="color: #1565c0; font-size: 20px;">Our <b>styled</b> paragraph.</p>
```

- `color`: #RGB, #RGBA, #RRGGBB, #RRGGBBAA. CSS alpha comes last, and the parser
  converts it to Android's packed ARGB order. Explicit colours do not adapt to themes.
- `font-size`: nonnegative decimal px values, plus unitless zero. No other units.
- Property names and px units ignore case. Last valid declaration wins for each
  supported property; invalid/unsupported declarations are ignored.
- Comments are skipped. Semicolons inside quoted/functional unsupported values do
  not create new declarations. This scanner is not a complete CSS tokenizer.

## Embedded CSS subset

```html
<style>p { font-size: 20px; } .notice { color: #1565c0; }</style>
<p class="notice">Our <b>styled</b> paragraph.</p>
```

`StyleResolver` reads all `<style>` elements before styling the tree, including
styles in a hidden `<head>` or after the affected element. It supports tag selectors
(`p`), class selectors (`.notice`), combined selectors (`p.notice`), and comma
lists. The only declarations are the same `color` and `font-size` values supported
inline. Class names match case-sensitively; a class attribute can contain multiple
space-separated names. More specific selectors win before source order, and inline
declarations win over these ordinary stylesheet rules. Each property cascades
independently, so a later color rule does not erase an earlier font size.

The parser examines at most 16 KiB of embedded CSS and keeps at most 128 rules per
document. Unsupported selectors and at-rules are skipped; CSS text stays hidden
from page rendering. External stylesheets, `!important`, IDs, descendant selectors,
media queries, named/functional colours, relative units, CSS-wide keywords, custom
properties and the CSS box model are not implemented. This is for controlled
learning fixtures, not arbitrary website compatibility.

## Units and painting

CSS px is a logical unit, not necessarily a physical screen pixel. StyleResolver
accepts a conversion scale and emits physical font pixels for the layout/measurer.
For the experimental renderer, HtmlPageView maps 1 CSS px to 1 Android sp so both defaults
and inline sizes follow device font scaling. This is our text-zoom policy,
not a universal mapping for CSS lengths. The JVM tests use a scale of 1.

Default text/link colours are tokens, resolved against the current Material theme
only when painting. Explicit hex colours are packed ARGB values. Thus theme-only
changes redraw without redoing styles or geometry. A document/font-scale change
reruns style resolution; a width change only reruns layout on the styled tree.

## Checkpoint

Run `StyleResolverTest`, `InlineStyleParserTest`, and `StyledLayoutTest` to verify the example above:
the blue paragraph has a larger font, and its bold child inherits that size and
colour. A following sibling returns to its parent's style.

Debug StyleResolver.resolveNode to watch inheritance and inline overrides, then
inspect a DrawText's style and bounds to see the resolved size affect geometry.

References: [CSS inheritance](https://www.w3.org/TR/css-cascade-5/#inheriting),
[cascade order](https://www.w3.org/TR/css-cascade-5/#cascade-sort),
[selectors](https://www.w3.org/TR/selectors-4/),
[hex colours](https://www.w3.org/TR/css-color-4/#hex-notation),
[CSS length units](https://www.w3.org/TR/css-values-4/#absolute-lengths).
