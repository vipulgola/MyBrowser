# MyBrowser

MyBrowser is an experimental Android privacy browser written in Kotlin. It offers two clearly labelled rendering modes:

- **Own engine**: a small Kotlin HTML parser, style resolver, layout engine, and Canvas renderer. It supports text, links, simple inline styling, bounded images, HTTPS navigation, search, and in-memory history. It is not a complete browser engine.
- **Compatible view**: Android WebView for sites the custom engine cannot render. It supports normal page navigation while keeping JavaScript off by default and disabling cookies, site storage, file access, and mixed HTTP content.

The app does not yet block trackers or provide a guaranteed incognito mode. HTTPS pages may load third-party resources, and WebView may retain internal profile data on the device. Some sites require cookies, storage, or JavaScript and may not work with the current defaults.

## Build

Open this directory in Android Studio and run the `app` configuration. From a terminal with a suitable Android SDK and JDK, use:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Implementation notes for each subsystem are in the `app/src/main/java/com/personal/mybrowser/` directories.
