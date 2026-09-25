#!/usr/bin/env bash
# AndroidBuilder for ir - the project's own Gradle wrapper is the build entry point:
# find the project that owns ./gradlew, chmod +x it, then run it.
set -e

TASK="${1:-assembleDebug}"
PROJECT_HINT="${2:-${ANDROIDBUILDER_PROJECT:-}}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ── 0. prerequisites ────────────────────────────────────────────────────────────
command -v java >/dev/null 2>&1 \
  || die "no JVM on PATH. Run 'Install JDK 17' from the AndroidBuilder panel first."

# AGP needs ANDROID_HOME; the panel passes it explicitly, otherwise fall back to the usual spots.
if [ -z "${ANDROID_HOME:-}" ]; then
  for CANDIDATE_SDK in "$HOME/Android/sdk" "$HOME/.Android/sdk" /opt/android-sdk; do
    if [ -d "$CANDIDATE_SDK" ]; then
      export ANDROID_HOME="$CANDIDATE_SDK"
      break
    fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ]; then
  die "ANDROID_HOME is not set. Run 'Install Android SDK' from the AndroidBuilder panel first."
fi
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
if [ ! -d "$ANDROID_HOME/build-tools" ] || [ -z "$(ls -A "$ANDROID_HOME/build-tools" 2>/dev/null)" ]; then
  die "$ANDROID_HOME has no build-tools. Run 'Install Android SDK' from the AndroidBuilder panel first."
fi
echo "  ANDROID_HOME: $ANDROID_HOME"
echo "  build-tools : $(ls -1 "$ANDROID_HOME/build-tools" | tr '\n' ' ')"

# ── 1. locate the project that owns ./gradlew ───────────────────────────────────
say "looking for a project with ./gradlew (task: $TASK)"
PROJECT_DIR=""

if [ -n "$PROJECT_HINT" ]; then
  [ -f "$PROJECT_HINT/gradlew" ] \
    && PROJECT_DIR="$PROJECT_HINT" \
    || die "no gradlew in '$PROJECT_HINT' (fix the project path in the AndroidBuilder panel)"
elif [ -f "$PWD/gradlew" ]; then
  PROJECT_DIR="$PWD"
else
  CANDIDATE="$(find /sdcard "$HOME" -maxdepth 5 -name gradlew -type f 2>/dev/null | head -n1)"
  if [ -n "$CANDIDATE" ]; then
    PROJECT_DIR="$(dirname "$CANDIDATE")"
  else
    die "no gradlew found under /sdcard or \$HOME (depth 5). cd into the project or export ANDROIDBUILDER_PROJECT=/path/to/project"
  fi
  if [ "$(find /sdcard "$HOME" -maxdepth 5 -name gradlew -type f 2>/dev/null | wc -l)" -gt 1 ]; then
    warn "several projects have a gradlew; using the first one: $PROJECT_DIR"
  fi
fi

cd "$PROJECT_DIR"
echo "  project: $PROJECT_DIR"

# ── 2. is the Gradle distribution already on disk? ─────────────────────────────
# The wrapper keeps every distribution it ever unpacked under
# $GRADLE_USER_HOME/wrapper/dists/<name>/<hash-of-url>/<name>.zip.ok, and it only downloads
# again when that .ok marker is missing. This is checked and reported up front, so it is clear
# whether a build is about to pull ~140 MB or reuse what is already there.
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_PROPS="$PROJECT_DIR/gradle/wrapper/gradle-wrapper.properties"
DIST_URL=""
[ -f "$WRAPPER_PROPS" ] && DIST_URL="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | tr -d '\\')"
if [ -n "$DIST_URL" ]; then
  DIST_NAME="$(basename "$DIST_URL")"
  DIST_NAME="${DIST_NAME%.zip}"
  # The hash directory name is Gradle's own, so match on the .ok marker instead of guessing it.
  if ls "$GRADLE_HOME_DIR"/wrapper/dists/"$DIST_NAME"/*/"$DIST_NAME.zip.ok" >/dev/null 2>&1; then
    echo "  gradle    : $DIST_NAME already installed, no download"
  else
    warn "gradle $DIST_NAME is not installed yet; this first build downloads it from $DIST_URL"
  fi
else
  warn "no distributionUrl in $WRAPPER_PROPS, cannot tell whether Gradle is already installed"
fi

# ── 3. run the wrapper ──────────────────────────────────────────────────────────
# /sdcard is a FUSE mount where Android grants no exec bit, so chmod silently does nothing and
# a direct ./gradlew dies with "Permission denied". The wrapper is a shell script, so it is run
# through bash and addressed by absolute path, never relatively.
[ -f "$PROJECT_DIR/gradlew" ] || die "no gradlew in $PROJECT_DIR (this is not a Gradle project)"
say "bash $PROJECT_DIR/gradlew $TASK"
bash "$PROJECT_DIR/gradlew" "$TASK" || die "./gradlew $TASK failed (exit $?)"

# ── 4. show what came out ───────────────────────────────────────────────────────
say "APK files"
APK_LIST="$(find "$PROJECT_DIR" -path '*/build/outputs/apk/*' -name '*.apk' -type f 2>/dev/null | sort)"
if [ -n "$APK_LIST" ]; then
  printf '%s\n' "$APK_LIST"
else
  echo "  no APK found under */build/outputs/apk"
fi
