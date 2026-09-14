# OpenCode Chat — GhostIDE plugin demo

A demo GhostIDE plugin that chats with the **opencode** coding agent (https://opencode.ai)
from inside the IDE over its HTTP **server API** (`opencode serve`), while showcasing the new
VS Code-style GhostIDE plugin UI API: `EditorPanel`, plus `EditorHost`, `PluginLogger` and
`PluginSetupAction`.

## What it demos

- **EditorPanel** — the new API: a chat panel hosted *inside* the running editor screen
  (side sheet, one toolbar button per panel) instead of a separate `PluginScreenActivity`.
  Registered at `PluginUiExtensionPoints.EDITOR_PANEL`.
- **opencode server API** (`client/OpenCodeClient.java`, plain `HttpURLConnection` + `org.json`):
  - `GET /global/health` — server version (button **Check**)
  - `GET /agent` — available agents (shown after **Check**)
  - `POST /session` — create a session (**New session**)
  - `POST /session/:id/message` — send a message and wait for the reply (**Send**)
- **EditorHost** (`IdeHostServices.EDITOR_HOST`):
  - **Attach editor file** — reads `getOpenFile()` + `getEditorText()` into the prompt
  - **Reply → editor** — writes the last assistant reply with `setEditorText(...)`
- **PluginLogger** — the plugin logs its activation via `context.getLogger()`.
- **PluginSetupAction** — a setup action that installs the opencode CLI inside the sandbox
  (offered by the host's plugin manager with a terminal handoff you confirm).

The previous full-screen `PluginScreen` API is still supported by the host; this demo was
migrated to the new `EditorPanel` API so the chat lives inside the editor.

## Before you build

Put the three SDK artifacts in `app/libs/` (replace the old ones) — from the
`ghostide-plugin-sdk` CI artifact, or locally from the Ghostide repo:

- `plugin-api-0.1.0.jar`
- `ide-api-0.1.0.jar`
- `ide-ui-api-release.aar`  (must contain `EditorPanel` / `PluginUiExtensionPoints.EDITOR_PANEL`)

`compileOnly` needs `androidx.appcompat` too; it's already declared in `app/build.gradle` and is
resolved at build time / provided at runtime by the host app.

## Build

```
./gradlew :app:assembleDebug
```

Rename `app/build/outputs/apk/debug/app-debug.apk` to `opencodechat.gpl` and install it from
GhostIDE → **Plugin manager** → **+**.

## Run opencode

Inside the GhostIDE sandbox terminal (or after running the plugin's setup action):

```
npm install -g opencode-ai
opencode serve          # defaults to http://127.0.0.1:4096
```

Then open any file in the editor and tap the **panel button** (right side of the toolbar) to
slide the Chat panel in, then tap **Check** — the chat connects to `http://127.0.0.1:4096`
(editable in the panel).

## Structure

```
app/src/main/java/com/opencode/chat/
  OpenCodeChatPlugin.java      # GhostPlugin entry: registers the EditorPanel, logs, setup action
  OpenCodeChatPanel.java       # EditorPanel -> id/title/createView
  OpenCodeChatView.java        # programmatic chat UI + EditorHost integration
  client/OpenCodeClient.java   # opencode HTTP client (health, agents, sessions, messages)
```
