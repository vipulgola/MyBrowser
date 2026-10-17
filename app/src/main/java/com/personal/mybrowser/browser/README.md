# Two rendering modes

MyBrowser now has two explicitly labelled modes:

- **Own engine** parses HTML, resolves its limited styling, lays out text/images, and draws the page itself. It does not run JavaScript or fetch CSS.
- **Compatible view** uses the Android system WebView for pages beyond our engine. It is a separate engine and does not increase the completeness of our own engine. Leaving this mode destroys its view and clears its navigation history, resource cache, form data, and WebStorage.

Compatible view accepts HTTPS top-level pages only. It disables cookies (including third-party cookies), DOM storage, database storage, file/content access, mixed HTTP content, and the resource cache. JavaScript starts off and the user can turn it on for the current compatible-view session. There is no JavaScript bridge, file chooser, download handler, or site permission grant. Safe Browsing stays enabled when available. The app opts out of WebView usage metrics and excludes its data from backup/transfer.

This is a privacy baseline, **not anonymity or tracker blocking**. HTTPS sites and their third-party HTTPS resources still receive network requests. The system WebView provider and Safe Browsing behavior are outside our engine. WebView creates internal profile files on disk even with cookies and site storage disabled, so this is not an incognito mode. Some sites need cookies or local storage and will not work in this mode. There are no tabs, downloads, persistent bookmarks, site permission controls, or a reliable cross-process private-browsing guarantee yet.
