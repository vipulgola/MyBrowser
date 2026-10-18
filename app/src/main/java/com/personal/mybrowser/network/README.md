# Network boundaries

Normal browsing uses Android WebView. This package supports only the optional experimental engine; it is not a shared HTTP cache or a replacement for WebView's network stack.

`PrivacyRelay` applies the saved connection mode before the browser UI is shown. Tor starts enabled and verifies a Tor exit through Orbot's local HTTP CONNECT proxy on `127.0.0.1:8118`. In Tor mode, the page loader, image loader, and ad-list updater use that same explicit proxy with no direct fallback; an unavailable Orbot blocks browsing. When the user disables Tor in Browser Settings, the WebView proxy override is cleared before pages can open and app-owned HTTPS connections use the normal network. Websites then see the device's normal public IP. Mode changes close open tabs. In Tor mode, WebView requires document-start JavaScript support so page scripts cannot create direct WebRTC peer connections. The relay changes the source IP seen by websites; it does not erase cookies or prevent account-based identification.

`HttpsAddress` validates and normalizes HTTPS URLs. It rejects credentials, invalid ports, control characters, and non-HTTPS schemes. `AddressInputResolver` turns address-bar search terms into a DuckDuckGo Lite URL without sending a request while the user types.

`HttpsPageLoader` performs one HTTPS GET at a time on an I/O dispatcher. It accepts only HTTP 200 HTML responses up to 128 KiB, with an eight-second timeout. It follows at most five HTTPS redirects after validating every destination; loops, invalid redirects, unsupported encodings, and oversized pages become readable errors. It uses the platform TLS checks, sends no cookies or referrer, and disables connection caching.

`HttpsImageLoader` accepts only same-origin HTTPS images. It limits redirects, bytes, media types, and load time; unsupported images are omitted. No third-party image request is made by the experimental engine.

The experimental engine keeps at most 20 successful HTML visits in memory through `OnlineHistory`. Back and Forward restore saved HTML without another request. Reload fetches again. Home clears the session and cancels a pending request. This is navigation history, not a disk cache or secure memory wiping.
