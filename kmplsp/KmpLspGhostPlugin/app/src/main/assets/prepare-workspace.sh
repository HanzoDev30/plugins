#!/usr/bin/env bash
# prepare-workspace.sh - makes one project usable by kmp-lsp before the server starts.
#
#   prepare-workspace.sh [ROOT]            # full: workspace.json, verify, extract sources
#   prepare-workspace.sh --quick [ROOT]    # what the launcher runs on every LSP start
#
# Why this exists: the server finds a project's own files by walking the workspace root, but
# everything that is *not* project code has to be pointed at explicitly.
#
#   * Android SDK  - gives android.jar, so Activity/Context/Compose resolve in a .java file
#   * Gradle cache - gives *-sources.jar, so library hover docs and go-to-def work
#
# The jars a project ships itself (app/libs/*.jar, build/libs/*.jar - the whole API surface of a
# Ghost IDE plugin, invisible to both the SDK and the Gradle cache) are handed to the server by the
# launcher, as initializationOptions.indexingOptions.jarPaths, and reported here.
#
# This script writes nothing into the project: a workspace.json that carries only jarPaths makes
# kmp-lsp report "workspace.json: auto-discovered 0 source roots" and drops the build-layout
# discovery that finds app/src/main/java, so the project would index even less, not more.
set -uo pipefail

# kmp-env.sh and the server live next to this script, wherever the plugin put them, so the location
# comes from the script itself and not from an environment variable the caller may not set.
KMP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" 2>/dev/null && pwd -P)"
[ -x "$KMP_DIR/kmp-lsp" ] || KMP_DIR="/opt/kmp-lsp"
REAL="$KMP_DIR/kmp-lsp"

QUICK=0
ROOT=""
for argument in "$@"; do
  case "$argument" in
    --quick) QUICK=1 ;;
    -*) ;;
    *) [ -z "$ROOT" ] && ROOT="$argument" ;;
  esac
done
ROOT="${ROOT:-$PWD}"
ROOT="$(cd "$ROOT" 2>/dev/null && pwd)" || {
  printf 'error: no such directory: %s\n' "${1:-.}" >&2
  exit 1
}

say() { printf ':: %s\n' "$*"; }
ok()  { printf '\033[32m  ok\033[0m %s\n' "$*"; }
warn() { printf '\033[33m  !!\033[0m %s\n' "$*" >&2; }

# shellcheck source=kmp-env.sh
[ -f "$KMP_DIR/kmp-env.sh" ] && . "$KMP_DIR/kmp-env.sh"

[ -x "$REAL" ] || {
  printf 'error: kmp-lsp is not installed at %s - run "Install KMP LSP" from the plugin manager.\n' \
    "$REAL" >&2
  exit 1
}

# ── 1. the project itself ──────────────────────────────────────────────────────
if [ -f "$ROOT/settings.gradle" ] || [ -f "$ROOT/settings.gradle.kts" ] \
   || [ -f "$ROOT/build.gradle" ] || [ -f "$ROOT/build.gradle.kts" ] \
   || [ -f "$ROOT/pom.xml" ] || [ -f "$ROOT/gradlew" ]; then
  ok "build system: Gradle/Maven project"
else
  warn "no settings.gradle/build.gradle/gradlew in $ROOT - only the jars below will be indexed"
fi

# ── 2. the Android SDK ────────────────────────────────────────────────────────
ANDROID_JAR="$(kmp_android_jar 2>/dev/null || true)"
if [ -n "$ANDROID_JAR" ]; then
  ok "android.jar: ${ANDROID_JAR#"$ROOT"/}"
else
  warn "no android.jar found under \$ANDROID_HOME/platforms - Android classes will not resolve."
  warn "install the SDK from the AndroidBuilder panel, then run this again."
fi

# ── 3. the Gradle cache ───────────────────────────────────────────────────────
if [ -n "${GRADLE_USER_HOME:-}" ] && [ -d "$GRADLE_USER_HOME/caches" ]; then
  SOURCES_COUNT="$(find "$GRADLE_USER_HOME/caches" -name '*-sources.jar' 2>/dev/null | wc -l)"
  ok "gradle cache: ${GRADLE_USER_HOME} (${SOURCES_COUNT} sources jar(s))"
  if [ "$SOURCES_COUNT" -eq 0 ]; then
    warn "no *-sources.jar in the cache yet - library hover docs stay empty until one build ran"
  fi
else
  warn "no Gradle cache at \${GRADLE_USER_HOME:-$HOME/.gradle}/caches - run one build first"
fi

# ── 4. the jars the project ships itself ──────────────────────────────────────
# <WORKSPACE> is kmp-lsp's own token for the project root, so the entries stay correct no matter
# how the rootfs mounts the storage.
jar_dirs() {
  local dir
  for dir in \
      "$ROOT"/*/libs \
      "$ROOT"/*/*/libs \
      "$ROOT"/*/build/libs \
      "$ROOT"/*/*/build/libs \
      "$ROOT"/libs \
      "$ROOT"/build/libs; do
    [ -d "$dir" ] || continue
    case "$dir" in
      */.gradle/*|*/node_modules/*) continue ;;
    esac
    # A directory is only worth indexing when it actually holds a jar or an aar.
    if find "$dir" -maxdepth 1 \( -name '*.jar' -o -name '*.aar' \) -print -quit 2>/dev/null \
        | grep -q .; then
      printf '%s\n' "${dir#"$ROOT"/}"
    fi
  done
}

mapfile -t JAR_DIRS < <(jar_dirs | awk '!seen[$0]++')
if [ "${#JAR_DIRS[@]}" -gt 0 ]; then
  ok "project jars: ${JAR_DIRS[*]}"
else
  warn "no app/libs or build/libs with jars under $ROOT"
fi

if [ -f "$ROOT/workspace.json" ]; then
  ok "workspace.json present (left untouched - the IDE owns that file)"
fi

# ── 5. what the indexer will actually see ─────────────────────────────────────
# kmp-lsp's own answers to "do you know this project" and "does the index build", so a failure
# here names its own cause instead of leaving the user with a server that resolves nothing.
say "what kmp-lsp discovers in $ROOT"
if "$REAL" sources --root "$ROOT" 2>&1 | sed 's/^/   /'; then
  ok "sources listed above"
else
  warn "'kmp-lsp sources' failed; the server will still start, with fewer libraries"
fi

say "building the index once, so the first completion is instant"
if "$REAL" index --root "$ROOT" 2>&1 | sed 's/^/   /'; then
  ok "index built"
else
  warn "'kmp-lsp index' failed; the server rebuilds it itself on first use"
fi

# ── 6. library sources (full mode only) ───────────────────────────────────────
# Only needed when the Gradle cache has no *-sources.jar: as of v0.21 the server mounts those
# itself, and a build downloads them only when an IDE asks for them, which nothing does here.
if [ "$QUICK" -eq 1 ]; then
  say "quick mode done - run 'kmp-lsp-prepare' once per project to extract library sources"
  exit 0
fi

if [ "${KMP_SKIP_EXTRACT:-0}" = "1" ]; then
  say "extract-sources skipped (KMP_SKIP_EXTRACT=1)"
  exit 0
fi

say "extracting library sources from the Gradle cache (first run can take a while)"
# Run from inside the project: extract-sources takes no --root and works on the cwd.
if (cd "$ROOT" && "$REAL" extract-sources 2>&1 | sed 's/^/   /'); then
  ok "library sources extracted into ${KMP_LSP_SOURCE_DIR:-$HOME/.kmp-lsp/sources}"
else
  warn "extract-sources failed; project and SDK symbols still work"
fi
