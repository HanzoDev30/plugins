# Code Snap (Ghost IDE plugin) - Android Gradle project

Turn the code selected in the editor into a syntax-highlighted PNG image.

Panel: `Snap` = highlight the editor selection and save it to `Pictures/CodeSnap` |
`Lang` = cycle auto-detect / manual language (180+ languages) | `Theme` = one-dark / github-dark |
`Share` = system share sheet for the last image.

Highlighting runs in a WebView on `file:///android_asset/web/index.html` with the packed
highlight.js v11.11.1 bundle (v11 `hljs.highlight(code, {language})` API) and two theme CSS files,
fully offline. Colors of the panel come from `M3Theme` (thememanager), the view is inflated from
`res/layout/codesnap_panel.xml` through a `ContextThemeWrapper`.

## Build
1. Copy `plugin-api.jar`, `ide-api.jar`, `ide-ui-api.aar`, `thememanager-release.aar` into `app/libs/`.
2. Open in Android Studio (or GhostIDE) and run `assembleDebug`, or `gradle assembleDebug`
   (`gradle-wrapper.jar` is not included - use your own Gradle 8.13+ or Android Studio).
3. Result: `app/build/outputs/apk/debug/app-debug.apk` - rename it to `codesnap.gpl`
   (zip: `classes.dex` + `assets/plugin.json` + `assets/icon.png` + `assets/web/*`).
4. Copy it to `filesDir/gpl_plugins/` and restart.

Versions used: AGP 8.13.0, Gradle 9.0, compileSdk/targetSdk 36, minSdk 26, Java 17.
