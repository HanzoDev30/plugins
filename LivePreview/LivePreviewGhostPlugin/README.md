# Live Preview (Ghost IDE plugin) - Android Gradle project

HTML/CSS/JS preview panel. The project folder is served to a WebView through a virtual
`https://ghostpreview.local/` origin (no sockets, no cleartext), reloads on save.

Panel: ◀ ▶ back/forward | ↻ reload | File = preview the open file (for css/js it loads the sibling or
project `index.html`) | Auto = reload on save | Log = console, JS alerts, 404s, load errors.

## Build
1. Copy `plugin-api.jar` and `ide-ui-api.aar` into `app/libs/`.
2. Open in Android Studio (or GhostIDE) and run `assembleGpl`, or `gradle assembleGpl`
   (`gradle-wrapper.jar` / `gradlew` are not included - Android Studio creates them, or use your own Gradle 8.13+).
3. Result: `livepreview.gpl` in the project root (zip: `classes.dex` + `assets/plugin.json` + `assets/icon.png`).
4. Copy it to `filesDir/gpl_plugins/` and restart.

Versions used: AGP 8.13.0, Gradle 8.13, compileSdk/targetSdk 36, minSdk 26, Java 17. Change them in
`build.gradle` / `app/build.gradle` / `gradle-wrapper.properties` to match your setup.
