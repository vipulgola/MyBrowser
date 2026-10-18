# MyBrowser

MyBrowser is an experimental Android privacy browser written in Kotlin. It starts on an empty home screen, with no bundled demo site. Enter a website or search in the address bar to browse. It has two rendering modes:

**IP privacy:** Tor is on by default. With Tor enabled, browsing is blocked until [Orbot](https://orbot.app/en/download/) is connected in Power User mode with its local HTTP proxy on port 8118. A VPN is not required. MyBrowser verifies a Tor exit before opening pages and routes WebView and experimental-engine HTTPS traffic through Orbot. If Orbot stops, requests fail rather than switching to your real IP. Browser Settings can turn Tor off; direct browsing then works without Orbot and websites see your normal public IP. Switching modes closes open tabs. Webpage geolocation is disabled. Tor changes the IP visible to sites, but signing into accounts, cookies, and browser fingerprinting can still identify you; some websites may reject Tor exits.

- **Normal browser**: Android WebView for real websites. It supports page navigation, search, and in-memory tabs with separate page histories. Website-requested new windows are blocked by default. Browser Settings can allow links you tap to open a new tab; automatic pop-ups stay blocked, and pop-ups never replace the current page. The **+ New** button still opens a tab. It blocks requests to known ad/tracker hosts and third-party cookies, and enables JavaScript, DOM storage, and first-party cookies for site compatibility. Menu can block JavaScript for the current HTTPS hostname. File access and mixed HTTP content remain disabled.
- **Experimental engine**: a small Kotlin HTML parser, style resolver, layout engine, and Canvas renderer. It supports text, links, simple inline and embedded CSS, bounded images, HTTPS navigation, search, and in-memory history. It does not execute JavaScript or play video itself. YouTube addresses and links open a labeled WebView panel inside Experimental mode; Back to engine restores the prior engine page. It is not a complete browser engine; switch to it from Menu when a page is open.

Ad blocking is on by default in normal mode. It uses a bundled snapshot of [Peter Lowe's ad and tracking server list](https://pgl.yoyo.org/adservers/) so it works offline. Menu lets you turn it off for the current session, see the number of blocked requests, or request an updated list over HTTPS. Host-based blocking cannot catch every ad: first-party ads, ads served from unlisted hosts, and requests hidden behind redirects may remain. The experimental engine does not use this list. The app does not provide guaranteed incognito mode; WebView may retain cookies and other site data on the device. Some sites can still reject WebView or require browser features this app does not provide.

**Website safety:** MyBrowser validates addresses and requires HTTPS. In normal mode, Android WebView Safe Browsing checks known threats where supported; on Android 8.1 and newer, MyBrowser blocks reported threats without offering a bypass. Menu → Site details shows the actual hostname and address so you can inspect them. HTTPS and a missing threat warning do not prove that a site is legitimate, especially a new phishing site. The experimental engine does not use WebView Safe Browsing.

MyBrowser can receive web links from other Android apps. On Android 10 and newer, use **Set as default browser** on the home screen to make it the handler for ordinary web links. Incoming links open in the normal browser after address validation. HTTP links are upgraded to HTTPS before any request; there is no insecure fallback, so a site without HTTPS will fail to load.

## Build

Open this directory in Android Studio and run the `app` configuration. From a terminal with a suitable Android SDK and JDK, use:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Implementation notes for each subsystem are in the `app/src/main/java/com/personal/mybrowser/` directories.
