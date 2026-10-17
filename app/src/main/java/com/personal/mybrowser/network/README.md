# Milestones 5–9: Fetch HTML, navigate, retain visits and follow redirects

`Address field -> AddressInputResolver -> HttpsAddress -> BrowserViewModel -> HttpsPageLoader -> LoadedHtml -> existing engine`

The loader owns fetching and decoding. It returns the URL and source HTML only;
it does not parse, style or draw. The UI passes that source to our existing pipeline.
Milestone 5 used one fixed example URL. Milestone 6 adds an address bar and
generalizes the loader to accept a validated HttpsAddress. Milestone 7 resolves
online links against the displayed page URL and submits them through that same
loader. Milestone 8 keeps a bounded in-memory online visit history with HTML
snapshots. Milestone 9 follows bounded HTTPS redirects and commits only the final
URL and HTML. The existing local pages/history remain available.

## Address parsing and normalization

- Leading/trailing whitespace is trimmed. An omitted scheme becomes https://.
  Protocol-relative input is also given HTTPS. Explicit HTTP/other schemes are
  rejected by the URL parser; there is no HTTP upgrade or search fallback for
  invalid URL-like input. Milestone 14 handles search terms before URL parsing.
- URI.parseServerAuthority validates the structured address without DNS/network IO.
  Missing/malformed hosts, embedded credentials, invalid ports, backslashes and
  embedded whitespace/control characters are rejected.
- Scheme/host become lowercase, default port 443 is omitted, and an empty path
  becomes /. Path/query case and percent escapes are preserved; Unicode path/query
  characters are encoded with URI.toASCIIString without double encoding existing escapes.
- ASCII DNS/punycode hosts, IPv4 and bracketed IPv6 are supported. Unicode domain
  conversion and IPv6 zone identifiers are not implemented. Common dotted-host:port
  and localhost:port inputs are recognized without confusing the colon for a scheme.
- Fragments stay in the displayed address but are excluded from the HTTP request.
  Fragment scrolling is not implemented yet. This is a strict URI-based subset,
  not the full WHATWG URL parser or a full browser omnibox.
- The loader accepts the validated type instead of an arbitrary string. URL parsing
  validates syntax/policy; it cannot guarantee that a host exists or a page is reachable.

## Online link resolution

`href + displayed page URL -> HttpsAddress.resolveLink -> existing load/error/retry flow`

- The base is Ready.page.url, never the editable address field. Local links still
  use the bundled page map; an online about.html does not open the local demo page.
- Relative paths, root paths, absolute HTTPS URLs and //host links are supported.
  Literal dot segments are removed, with parent traversal stopping at the host root.
  Query-only links keep the current document path; empty/fragment-only references
  keep its query too. Raw percent escapes and repeated slashes remain intact.
- Link parsing uses URI components and RFC 3986 section 5.2 resolution rules for
  this HTTPS subset. Unlike address-bar input, other.com in an href is a relative
  path, and other.com:8443/page is an unsupported scheme, not a guessed host.
- The resolved URL goes through the existing credentials/host/port validation.
  Invalid, HTTP and other-scheme links display a message without discarding the
  current page or draft. No external app is launched. Accepted links update the
  address field and use the existing cancellation, loading, error and Retry logic.
- Fragments remain in the displayed URL and are omitted from requests. Following
  a fragment link currently reloads the document; scrolling to an element is not
  implemented. HTML base elements are not supported yet. Attribute values now use the
  parser's limited semicolon-required entity decoder (milestone 10). This remains a strict subset, not WHATWG URL/HTML conformance.

## Request policy

- HttpsURLConnection runs inside withContext(Dispatchers.IO). Its default TLS
  trust manager and hostname verifier are unchanged. Cleartext is disabled in the
  manifest; INTERNET is the only new permission.
- A GET request accepts text/html. HTTP 200 supplies the final HTML. Statuses
  301, 302, 303, 307 and 308 are handled explicitly as redirects (see below).
- Connection and read timeouts are each 8 seconds per connection/read, not a total
  request or redirect-chain deadline. Multiple slow hops can take longer.
- The body is bounded to 128 KiB, checked both against Content-Length and while
  streaming, including when a server omits or understates that header.
- Accept-Encoding requests identity; unexpected compression is rejected. The
  HTTP charset is honoured; UTF-8 is used when it is absent. HTML meta/BOM encoding
  detection is not implemented. Missing/non-HTML Content-Type is rejected.
- The stream closes and the connection disconnects on success, rejection or failure.
  Cache use is disabled and Cache-Control: no-store is sent. No cookie jar or disk
  response cache is configured by this loader. This is not a complete browser
  networking stack or a claim of anonymous browsing.
- This HTML loader requests only the document. Milestone 16 added a separate,
  bounded image loader; CSS, scripts and other subresources are not fetched.

## Explicit HTTPS redirects

- Automatic platform redirects stay disabled. The loader follows at most five
  Location hops (six requests including the initial request), always using GET.
  This milestone does not implement POST/body redirect semantics.
- Each nonempty Location resolves against the URL that returned that response.
  Relative paths and cross-host HTTPS destinations are supported. Existing address
  validation rejects HTTP downgrades, credentials, unsupported schemes and malformed
  destinations before another connection opens. Missing/blank Location is an error.
- Every hop uses the existing TLS defaults, timeouts, request headers and disabled
  cache setting. The previous connection disconnects in finally before the next
  one opens; intermediate response bodies are never opened by our loader.
- A set of visited normalized request URL strings detects repeated destinations.
  Fragments are excluded from that comparison and from HTTP requests. Other URI
  aliases may reach the hop limit instead of being detected as identical URLs.
- Per RFC 9110 section 10.2.2, an omitted fragment in Location inherits the current
  fragment; an explicit fragment (including empty #) replaces it. Fragment scrolling
  remains unsupported. Other 3xx statuses are reported as HTTP errors.
- The final response must satisfy all existing HTML, charset, compression and size
  checks. Cancellation is checked before each connection and after response headers,
  as well as during final-body reads. Socket cancellation is still cooperative.
- LoadedHtml carries the final URL. The ViewModel updates the address field if it
  still contains the submitted URL; an unsubmitted edit is preserved. The caption,
  relative-link base and single history entry always use the final URL. Intermediate
  hops never become history entries. If the chain fails, Retry restarts at the
  original submitted URL and Back can restore the previous successful visit.

## Request ownership and UI

WebPageState has Local, Loading(address), Ready(page) and Failed(address, message)
states. The editable addressInput is separate from the submitted address. Invalid
drafts leave the current page/request untouched. Editing alone does not navigate;
Go or the keyboard's Go action submits. A new different address replaces the current
request; duplicate submissions for the same in-flight address are ignored. Retry
uses the failed state's address, not a subsequently edited draft, and resets the
field to that submitted address. The caption shows the actual request/page address.
ViewModelScope owns the
job, so rotation retains the request/result and clearing the ViewModel cancels it.
Back or Local demo invalidates the request id and cancels the job. Back restores
a saved online visit when available; Local demo ends the online session and reveals
the previous local visit.
Cancellation is rethrown; late successes/errors cannot overwrite a newer state.
Blocking Java socket calls may continue until their timeout; cancellation does not
guarantee an immediate socket abort. Checks between reads and coroutine result
delivery keep cancelled content from being published.

## In-memory online history

OnlineHistory holds an ordered private list of LoadedHtml values (URL + source HTML)
and a currentIndex. BrowserViewModel owns its current immutable history value. It
retains at most 20 visits, including any forward branch; old entries are evicted
when a successful new visit exceeds that limit. The loader's existing body limit
still applies to each fetched page. This is an entry-count bound, not a measurement
or exact cap on total JVM heap usage.

- Only successful, still-current requests add visits. Loading, failure, invalid
  input and cancelled/stale responses never append or move the history cursor.
- Repeated URLs are distinct visits and retain their individual HTML snapshots.
- From a Ready page, Back moves the cursor to the previous visit and passes its
  HTML through the existing rendering pipeline, without calling the loader.
  The address field and page caption return to that visit's URL.
- From Loading or Failed, Back returns to the current committed visit, without
  stepping past it. If no successful visit exists, Back returns to the local demo.
- New successful navigation after Back drops the forward branch: A -> B -> C,
  Back to B, then D gives [A, B, D]. Forward restores retained snapshots through the navigation controls (milestone 13).
- Back beyond the oldest retained visit, or the Local demo button, clears the
  online history and returns to the previous local visit. Local history is separate.
- Snapshots are URL/HTML only: no View, Context, DOM or layout is kept per entry.
  Scroll-position restoration is not implemented. The current renderer rebuilds
  from saved HTML when the restored document changes.
- Nothing is written to a file, Room, DataStore or SavedStateHandle. Rotation reuses
  the ViewModel; process destruction loses the session. Backgrounding alone does
  not erase it. Clearing history releases references; it is not secure memory wiping.

This is navigation history with HTML snapshots, not an HTTP response cache. It
restores the saved version even if the live page has changed. The loader still
bypasses the HTTP cache; HTTP freshness/revalidation is not implemented here.

## Verification

The normal unit tests use injected fake HTTPS connections and coroutine test
dispatchers. They cover decoding, validation, size limits, cleanup, failure/retry,
cancelled jobs and stale responses without relying on a live website. Link tests
cover path/query/fragment resolution, unsupported schemes, malformed URLs, escaping,
using the displayed URL despite draft edits, and retrying a failed link destination.
History tests cover offline Back without loader calls, distinct repeated visits,
forward-branch replacement, eviction, error/cancellation recovery, stale callbacks,
retry committing once and clearing the online session without changing local history.
Redirect tests cover all five supported statuses, relative and cross-host locations,
the exact hop boundary, loops, rejected destinations, fragment inheritance, per-hop
cleanup and cancellation, destination TLS failure, final-body limits, final-URL
history/link behavior and preserving an unsubmitted draft.

Run the app, enter example.com and press Go. Expect the site's paragraph about
documentation examples after loading (the live page's exact content may change). Then
use Back to return to the local demo. With the device offline, try again and check
the error/retry state; use Local demo while loading to cancel. Rotate during a
request or after it completes and verify the current network state remains.
Try example.org as another address, and explicit http://example.com to check field
validation. When a request fails, edit the draft without submitting, then use Retry
and check that it retries the original failed URL. Use simple static HTML pages:
the engine's parser, styling, shaping and layout remain experimental subsets.

For the link checkpoint, open
https://www.w3.org/History/19921103-hypertext/hypertext/WWW/TheProject.html
and tap "hypermedia". Its relative href WhatIs.html should resolve to the same
WWW directory, update the address field and display the HyperText page. Editing
the address field without submitting must not change that link's base. This is a
public historical fixture, not a bundled or guaranteed service.

For the history checkpoint, load the W3C page above and follow hypermedia. Once
both pages have loaded, disable Wi-Fi/mobile data and press Android Back. Expect
the original World Wide Web page and its original URL immediately, without a
loading spinner or network error. Press Back again to reach the local demo.
Also try a destination that fails: Back should restore the most recent successful
online page, not skip past it. Rotation should preserve the same visit sequence.

For the redirect checkpoint, open:
https://httpbin.org/redirect-to?url=https%3A%2F%2Fexample.com%2F
Expect the example.com HTML and final https://example.com/ address. Load another
page before this test; Back should return directly to it, without stopping at an
httpbin redirect entry. This public test service can be temporarily unavailable.

IANA provides this endpoint on a best-effort basis for examples, not as a production
dependency. A temporary failure should appear as an error, not a bundled fallback
presented as if it came from the internet.

References: [Android network operations](https://developer.android.com/develop/connectivity/network-ops/connecting),
[coroutine practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices),
[IANA example domains](https://www.iana.org/help/example-domains).

Link resolution references: [RFC 3986 section 5.2](https://www.rfc-editor.org/rfc/rfc3986#section-5.2),
[Java URI components](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html).

Redirect reference: [RFC 9110 Location and fragment inheritance](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.2).

## Rendering rejection after a successful download

Milestone 12 applies the parser's 128-element-depth and 10,000-node defaults to
both local and fetched HTML. PageContent maps expected complexity rejections to a
readable error before styling/layout. The successful HTTP visit remains in history;
Back restores the prior visit, and new addresses can still be submitted. The body
byte limit and parser structure limits address different constraints.

## Basic navigation controls (milestone 13)

The toolbar exposes Back, Forward and Reload; Reload changes to Stop while loading.
Back is disabled at the start of local history. Forward is enabled only when the
current local or Ready online visit has a retained next entry; it is disabled while
loading or showing a request failure. Android Back and the toolbar share back().

- Back/Forward restore saved online HTML without fetching. Local history now also
  has a cursor and forward branch. Successful new navigation drops that branch.
- Reload fetches the displayed page URL, not an unsubmitted address draft. Its
  successful final URL/HTML replaces the current visit without appending or
  deleting forward visits. Local Reload rebuilds the bundled page without a visit.
- Failed request state retains whether it was a reload. Retry (or Reload on the
  error screen) preserves that intent. A new Go submission is a normal new visit.
- Stop cancels and invalidates the active request, then restores the current saved
  visit without moving the cursor. With no online visit, it restores the local
  page. Late responses cannot overwrite it. Stop when idle is a no-op.
- History is observable independently of Ready content, so navigation availability
  updates even when successive visits contain identical URLs and HTML.
- Leaving online history for Local demo still clears the online session. Scroll
  restoration remains deliberately deferred; no search or privacy-control changes
  are part of this step.

Device checkpoint: load A and B, use Back then Forward, and verify both work offline.
Reconnect and Reload B; Back should return to A without a duplicate B visit. During
a new slow load, press Stop and verify the previously displayed page returns. The
same Back/Forward/Reload controls also work with the bundled local pages.

## Address-bar search (milestone 14)

AddressInputResolver classifies input only when Go is pressed. Website-like input
uses the existing HttpsAddress validation. Other single words and phrases become
an HTTPS request to https://lite.duckduckgo.com/lite/?q= with a UTF-8 form-encoded
query. There are no suggestions or requests while typing. Submitted search terms
are sent to DuckDuckGo; they are not processed locally.

Examples: example.com opens https://example.com/, while android development
searches with q=android+development. Reserved characters and Unicode are encoded
as one query value, so an ampersand cannot add another query parameter.

Explicit schemes, leading slash/bracket, and spaceless dotted hosts, ports,
credentials or localhost are treated as URL-like. Invalid URL-like input remains
an error instead of being sent to search. This is a deliberately small heuristic:
site:example.com is currently rejected as a scheme, a dotted single-word query is
treated as a host, and other single-label hosts require explicit HTTPS. Empty
input, control characters and backslashes are rejected.

The resolved search URL uses the same loading, retry, redirect and history flow.
Retry keeps the original query even after editing the draft; successful search
pages and result links participate in Back/Forward normally. No disk storage was
added. At this milestone the engine did not yet support forms, images or JavaScript.

Verification: unit tests cover classification, UTF-8/reserved-character encoding,
invalid inputs, no requests while typing, search/result history and retry after a
draft edit. All 214 unit tests passed; debug assembly and lint passed (18 warnings).
The original DuckDuckGo HTML endpoint returned HTTP 202 with a human-verification
challenge both in a desktop check and on the user's device. DuckDuckGo Lite is
their simpler non-JavaScript version. A desktop request with MyBrowser's request
headers returned HTTP 200, a 24 KiB HTML page and result links. This does not
guarantee that every device/network will receive the same response. The loader
still rejects a challenge response, and the engine cannot complete challenges.

Device checkpoint: first load example.com. Then type android development and
check that typing alone does not load anything. Press Go and inspect whether Lite
results appear. Open a result and use Back to return. An HTTP 202 challenge is
still possible and is not a successful search.

Reference: [DuckDuckGo non-JavaScript versions](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript).

## One-field GET forms and search again (milestone 15)

PageForm walks the parsed document and offers the first supported form as an
Android text field and button above the painted page. General support is limited
to one named text/search input, optional hidden inputs and one submit button in
a GET form. The current renderer cannot place native controls inline among Canvas
text. Editing a field does not send a request; tapping the button or keyboard Go
builds an application/x-www-form-urlencoded UTF-8 query. The action is resolved
against the displayed page, its old query is replaced, and the resulting URL must
pass the same HTTPS validation as a link. A successful submission becomes one
normal visit, so Back restores the prior page. An invalid action leaves it intact.

DuckDuckGo Lite's visible search form declares POST. For this one verified origin
and action (/lite/), PageForm offers a dedicated Search again control that sends
only its q field to Lite's working GET URL. It does not send the page's other POST
controls. Other POST forms, multiple editable fields, selects, password inputs,
uploads, disabled controls and scripted form behavior remain unsupported. A form
with unsupported controls is not offered as a partial submission.

Tests cover GET action/query resolution, encoding and hidden fields, rejected
HTTP actions and unsupported controls, the Lite origin restriction, and search
submission through online history. A live request through HttpsPageLoader and
HtmlParser found Lite's actual form and produced the expected new search URL.

Device checkpoint: search for android development. Above the result text, edit
the Search again field to kotlin coroutines and tap Search. The results and
address should change; Back should restore the first search without loading.

Form serialization reference: [HTML Standard form submission](https://html.spec.whatwg.org/multipage/form-control-infrastructure.html).

## Bounded same-origin images (milestone 16)

The layout engine now reserves a box for each img element, using its width/height
attributes when present and a small default otherwise. The box is capped to the
page width, and image links remain tappable. HtmlPageView fetches at most eight
distinct image sources per page through HttpsImageLoader. Only HTTPS URLs on the
document's origin are eligible; redirects cannot leave that origin. The loader
accepts PNG, JPEG, WebP and GIF responses up to 512 KiB, with no cookies,
referrer or disk cache. Decoding samples images to at most roughly one megapixel.
If an image is blocked or fails, its alt text stays visible. SVG and animated
frames are not rendered. Images are scaled to fit reserved boxes without
distorting their aspect ratio.

The live W3C MarkUp page returned 61 KiB of HTML; the parser/layout found three
image boxes, and our image loader retrieved a same-origin PNG. Unit tests cover
box geometry, linked-image hits, same-origin requests, blocked third-party/HTTP
images, cross-origin redirects, response MIME and body bounds.

Device checkpoint: open https://www.w3.org/MarkUp/ and look for the small
Interaction image near the page links. The W3C logo on that page is SVG and stays
as an alt-text placeholder. This public page can change independently of the app.
