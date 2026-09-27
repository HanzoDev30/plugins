#!/usr/bin/env bash
# build.sh - builds the KMP LSP plugin and packages it as the .gpl the store expects.
#
#   ./build.sh            release build (unsigned APK -> kmplsp.gpl, like every other plugin)
#   ./build.sh debug      debug build
#   ./build.sh --clean    clean first
#
# The store serves <folder>/<name>.gpl, which is just the built APK: the .gpl files already in
# this repository contain exactly classes.dex + AndroidManifest.xml + resources.arsc + assets/,
# i.e. an unsigned assembleRelease output. Renaming the APK is therefore all this script does
# beyond invoking Gradle - no signing, no zip surgery.
#
# open.json is NOT touched here: sync_plugins.py reconciles against the remote HanzoDev30/plugins
# tree, so a brand-new plugin only appears in the list after it is pushed.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$HERE/KmpLspGhostPlugin"
VARIANT="release"
CLEAN=0
GPL="$HERE/kmplsp.gpl"

say()  { printf '\033[36m::\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m  ok\033[0m %s\n' "$*"; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
  case "$1" in
    release|debug) VARIANT="$1"; shift ;;
    --clean|-c)    CLEAN=1; shift ;;
    -h|--help)     usage; exit 0 ;;
    *)             die "unknown argument: $1 (try --help)" ;;
  esac
done

# ── preconditions ────────────────────────────────────────────────────────────
[ -x "$PROJECT/gradlew" ] || die "gradlew missing in $PROJECT"
if [ -n "${JAVA_HOME:-}" ]; then
  say "JAVA_HOME=${JAVA_HOME}"
elif command -v java >/dev/null 2>&1; then
  say "java: $(command -v java)"
else
  die "no JDK found - install one (e.g. 'sudo apt install -y openjdk-17-jdk') or set JAVA_HOME"
fi

[ -f "$HERE/doc.json" ] || die "doc.json is missing from $HERE - the store rejects plugins without it"
[ -f "$HERE/kmplsp.png" ] || die "kmplsp.png icon is missing from $HERE - the store rejects plugins without an icon"

# ── build ────────────────────────────────────────────────────────────────────
cd "$PROJECT"
if [ "$CLEAN" -eq 1 ]; then
  say "clean"
  ./gradlew --console=plain clean >/dev/null
fi

TASK="assemble$(printf '%s' "$VARIANT" | sed 's/^./\U&/')"
say "gradle ${TASK} (wrapper $(sed -n 's/.*gradle-\(.*\)-bin\.zip.*/\1/p' gradle/wrapper/gradle-wrapper.properties))"
./gradlew --console=plain "$TASK"

APK_DIR="$PROJECT/app/build/outputs/apk/$VARIANT"
[ -d "$APK_DIR" ] || die "no APK produced in $APK_DIR"
APK="$(find "$APK_DIR" -maxdepth 1 -name '*.apk' | sort | head -n1)"
[ -n "$APK" ] || die "no *.apk found in $APK_DIR"

# ── sanity-check the payload before shipping it ──────────────────────────────
# A silently missing asset still yields a buildable APK that installs and then does nothing, so
# the checks that matter are the ones the host reads at runtime.
if command -v unzip >/dev/null 2>&1; then
  LISTING="$(unzip -Z1 "$APK")"
elif command -v python3 >/dev/null 2>&1; then
  LISTING="$(python3 -c 'import sys,zipfile;print("\n".join(zipfile.ZipFile(sys.argv[1]).namelist()))' "$APK")"
else
  LISTING=""
  printf '\033[33m  !!\033[0m no unzip/python3 - skipping APK content check\n' >&2
fi

if [ -n "$LISTING" ]; then
  for required in assets/plugin.json assets/install-kmp-lsp.sh assets/kmplsp.png classes.dex; do
    printf '%s\n' "$LISTING" | grep -qx "$required" \
      || die "$APK is missing $required - refusing to package it"
  done
  ok "APK contains plugin.json, installer, icon and classes.dex"
  ID="$(unzip -p "$APK" assets/plugin.json 2>/dev/null \
        | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  [ -n "$ID" ] && ok "plugin id: $ID"
fi

# ── package ──────────────────────────────────────────────────────────────────
cp "$APK" "$GPL"
chmod 0644 "$GPL"
ok "$(basename "$APK") -> $GPL"
printf '     %s\n' "$(du -h "$GPL" | cut -f1)  sha256 $(sha256sum "$GPL" | cut -c1-16)…"

cat <<EOF

Next:
  1. Install it: copy $GPL to the device, then open it from Ghost IDE's plugin manager
     (or 'adb install-multiple' the split APKs if you ever add them).
  2. Run the plugin's "Install KMP LSP" setup action once inside the rootfs.
  3. Open a .kt / .java / .swift file; the provider claims kt, kts, java and swift.
  4. Push the folder, then run ../sync_plugins.py from the repository root to refresh open.json.
EOF
