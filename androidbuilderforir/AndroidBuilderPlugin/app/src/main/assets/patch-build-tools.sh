#!/usr/bin/env bash
# AndroidBuilder for ir - replace the x86_64 binaries inside build-tools with arm64 ones
# and make Gradle use the local aapt2 instead of downloading it from Google.
set -e

SDK_DIR="${ANDROID_HOME:-$HOME/Android/sdk}"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools"
GRADLE_USER_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
PATCHER_URL="${ANDROIDBUILDER_PATCHER_URL:-https://raw.githubusercontent.com/Commit451/android-arm-build-tools/main/install.sh}"
PATCHER="$SDK_DIR/android-arm-build-tools-install.sh"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[ -d "$BUILD_TOOLS_DIR" ] || die "no build-tools at $BUILD_TOOLS_DIR. Run 'Install Android SDK' first."
command -v curl >/dev/null 2>&1 || { apt-get update -qq; apt-get install -y -qq curl; }
command -v unzip >/dev/null 2>&1 || { apt-get install -y -qq unzip; }

# ── 1. fetch the arm64 patcher ──────────────────────────────────────────────────
say "downloading the arm64 build-tools patcher"
curl -fsSL --retry 3 "$PATCHER_URL" -o "$PATCHER" || die "could not download $PATCHER_URL"
[ -s "$PATCHER" ] || die "downloaded patcher is empty: $PATCHER_URL"
chmod +x "$PATCHER"

# ── 2. patch every installed build-tools ────────────────────────────────────────
PATCHED_ANY=0
for dir in "$BUILD_TOOLS_DIR"/*/; do
  [ -d "$dir" ] || continue
  version="$(basename "$dir")"
  if [ -f "$dir/.patched" ]; then
    echo "  $version: already patched"
    continue
  fi
  say "patching build-tools $version for arm64"
  if (cd "$SDK_DIR" && bash "$PATCHER" --version "$version"); then
    touch "$dir/.patched"
    PATCHED_ANY=1
    echo "  $version: done"
  else
    warn "$version: patching failed, leaving it untouched"
  fi
done

[ "$PATCHED_ANY" = 1 ] || echo "  nothing new to patch"

# ── 3. tell Gradle to use the local aapt2 ──────────────────────────────────────
AAPT2=""
for candidate in "$BUILD_TOOLS_DIR"/*/aapt2; do
  [ -x "$candidate" ] && AAPT2="$candidate"
done

if [ -n "$AAPT2" ]; then
  say "pinning android.aapt2FromMavenOverride to $AAPT2"
  mkdir -p "$GRADLE_USER_DIR"
  touch "$GRADLE_USER_DIR/gradle.properties"
  sed -i '/^android\.aapt2FromMavenOverride=/d' "$GRADLE_USER_DIR/gradle.properties"
  printf 'android.aapt2FromMavenOverride=%s\n' "$AAPT2" >> "$GRADLE_USER_DIR/gradle.properties"
  echo "  $GRADLE_USER_DIR/gradle.properties updated"
else
  warn "no aapt2 found under $BUILD_TOOLS_DIR, skipping the gradle.properties override"
fi

say "done"
echo "Next: 'Build debug APK' runs ./gradlew assembleDebug for you."
