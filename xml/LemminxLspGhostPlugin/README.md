# Lemminx (XML) LSP Plugin

Routes `.xml` files to the real **Lemminx** XML language server (completion/diagnostics/hover),
running inside the same proot rootfs as JDT-LS (Java) and rust-analyzer — no new proot/rootfs
code, just the `ProotProcessLauncher` service from `ide-ui-api`.

## Before building

Put the three SDK files into `app/libs/` (same as for HelloGhostPlugin/RustLspGhostPlugin — from
the `ghostide-plugin-sdk` CI artifact, or built locally with `./gradlew :plugin-api:jar
:ide-api:jar :ide-ui-api:assembleRelease`):
- `plugin-api-0.1.0.jar`
- `ide-api-0.1.0.jar`
- `ide-ui-api-release.aar`

(They are already copied over from the Rust plugin project.)

## Build and install

```
./gradlew :app:assembleDebug
```

Rename the output (`app-debug.apk`) to `lemminx.gpl` and install it from the Plugin Manager
(+ -> pick file), same as before.

## Installing Lemminx inside the rootfs

This plugin declares a `PluginSetupAction` (`apt-get install -y lemminx` — the Debian package
installs `/usr/bin/lemminx`, a wrapper that runs the Lemminx uber-jar with the system Java). After
installing the `.gpl`, a dialog with a "Run in terminal" button should appear — press it, the
terminal opens with the pre-filled command, you press Enter to confirm (deliberately not
automatic; you should see and confirm the command).

## Test

Open an `.xml` file in GhostIDE. If `lemminx` is installed, you should get real completion /
diagnostics / hover (not the simple keyword-based autocomplete).

## Known limitation

`XmlLanguage.getFormatter()` is not wired to this LSP — the same limitation as Rust: format-on-save
is not picked up from the language server, but completion/diagnostics/hover are.
