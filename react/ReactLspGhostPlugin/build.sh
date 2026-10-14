#!/usr/bin/env bash
#
# GhostIDE plugin builder — no Gradle, no aapt, no resources.
# Compiles app/src/main/java against android.jar + app/libs, produces classes.dex
# with d8 and packages a ready-to-install .gpl (APK) including all assets.
#
# Usage (run with bash, /sdcard has no exec bit):
#   bash build.sh                       <- builds the project this script lives in
#   bash build.sh <project-dir>         <- builds another project
#   bash build.sh <project-dir> out.gpl <- picks the output name
#
# Required: a JDK on PATH (javac/jar/java). d8 is auto-fetched (cached once).
#

set -euo pipefail

usage() {
  echo "usage: bash build.sh [<project-dir>] [output.gpl]"
  echo "  no-arg: builds the project this script lives in"
  exit 1
}

ROOT_TOOLS="${BUILD_TOOLS_DIR:-/storage/emulated/0/AndroidIDEProjects/.buildtools}"
ANDROID_JAR="${ANDROID_JAR:-/storage/emulated/0/ghostide/android.jar}"
MIN_API="${MIN_API:-26}"
BT_URL="${BT_URL:-https://maven.myket.ir/android-sdk/build-tools_r35.0.1_linux.zip}"
BUILD_START=$SECONDS

# ── locate project ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ "$#" -ge 1 ]; then
  PROJ="$(cd "$1" 2>/dev/null && pwd)" || usage
elif [ -d "$SCRIPT_DIR/app/src/main/java" ]; then
  PROJ="$SCRIPT_DIR"
elif [ -d "$(dirname "$SCRIPT_DIR")/app/src/main/java" ]; then
  PROJ="$(dirname "$SCRIPT_DIR")"
else
  usage
fi

[ -d "$PROJ/app/src/main/java" ] || { echo "error: no app/src/main/java in: $PROJ" >&2; exit 1; }

# ── output name ─────────────────────────────────────────────────────────────
if [ "$#" -ge 2 ]; then
  OUT="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
else
  base="$(basename "$PROJ")"
  base="${base%%GhostPlugin}"   # JsonLspGhostPlugin -> JsonLsp
  OUT="$PROJ/${base,,}.gpl"     # -> jsonlsp.gpl
fi

SRC="$PROJ/app/src/main"
LIBS="$PROJ/app/libs"

# ── javac / jar / java ──────────────────────────────────────────────────────
for bin in javac jar java; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "error: '$bin' not found on PATH (install a JDK, e.g. temurin-17/25)" >&2
    exit 1
  fi
done

# ── d8 (cached) ─────────────────────────────────────────────────────────────
D8="$ROOT_TOOLS/d8.jar"
if [ ! -f "$D8" ]; then
  echo ">> downloading build-tools (d8) from myket mirror ..."
  mkdir -p "$ROOT_TOOLS"
  curl -sSL -o "$ROOT_TOOLS/bt.zip" "$BT_URL"
  python3 -c "import zipfile, sys; zipfile.ZipFile(sys.argv[1]).extract('android-15/lib/d8.jar', sys.argv[2])" "$ROOT_TOOLS/bt.zip" "$ROOT_TOOLS"
  mv "$ROOT_TOOLS/android-15/lib/d8.jar" "$D8"
  rm -rf "$ROOT_TOOLS/android-15" "$ROOT_TOOLS/bt.zip"
fi

WORK="$(mktemp -d /tmp/gpl-build-XXXXXX)"
trap 'rm -rf "$WORK"; echo ">> elapsed: $((SECONDS - BUILD_START))s"' EXIT

# ── classpath: android.jar + every jar/aar in app/libs ─────────────────────
CP="$ANDROID_JAR"
i=0
for f in "$LIBS"/*.jar; do
  [ -f "$f" ] && CP="$CP:$f"
done
for f in "$LIBS"/*.aar; do
  [ -f "$f" ] || continue
  i=$((i+1))
  d="$WORK/aar$i"
  mkdir -p "$d"
  python3 -c "import zipfile, sys; zipfile.ZipFile(sys.argv[1]).extract('classes.jar', sys.argv[2])" "$f" "$d"
  CP="$CP:$d/classes.jar"
done

# ── compile ─────────────────────────────────────────────────────────────────
FLIST=$(find "$SRC/java" -name "*.java")
if [ -z "$FLIST" ]; then
  echo "error: no .java sources under $SRC/java" >&2
  exit 1
fi

mkdir -p "$WORK/classes"
if ! javac --release 17 -g -cp "$CP" -d "$WORK/classes" $FLIST; then
  echo ">> --release 17 failed, retrying as plain javac ..."
  javac -g -cp "$CP" -d "$WORK/classes" $FLIST
fi

# ── dex ─────────────────────────────────────────────────────────────────────
mkdir -p "$WORK/dex"
( cd "$WORK/classes" && jar cf "$WORK/all.jar" . )
java -cp "$D8" com.android.tools.r8.D8 --release --min-api "$MIN_API" \
  --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/all.jar"

# ── package .gpl ────────────────────────────────────────────────────────────
OUTDIR="$(dirname "$OUT")"
mkdir -p "$OUTDIR"
python3 - "$WORK/dex/classes.dex" "$SRC/assets" "$ROOT_TOOLS/template" "$OUT" <<'PY'
import sys, os, zipfile
dex, assets, templ, out = sys.argv[1:]
z = zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED)
z.write(dex, "classes.dex", compress_type=zipfile.ZIP_STORED)
for n in sorted(os.listdir(templ)):
    p = os.path.join(templ, n)
    if os.path.isfile(p):
        z.write(p, n, compress_type=zipfile.ZIP_STORED)
if os.path.isdir(assets):
    for root, _, files in os.walk(assets):
        for f in files:
            p = os.path.join(root, f)
            z.write(p, "assets/" + os.path.relpath(p, assets))
z.close()
print("entries:")
with zipfile.ZipFile(out) as r:
    for n in r.namelist():
        print("  %s (%d b)" % (n, r.getinfo(n).file_size))
PY
echo "OK: $OUT ($(du -h "$OUT" 2>/dev/null | cut -f1)) in $((SECONDS - BUILD_START))s"