#!/usr/bin/env bash
# AndroidBuilder for ir - replace the x86_64 binaries inside build-tools with arm64 ones
# and make Gradle use the local aapt2 instead of downloading it from Google.
set -e

SDK_DIR="${ANDROID_HOME:-$HOME/Android/sdk}"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools"
GRADLE_USER_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
REPO="Commit451/android-arm-build-tools"
RELEASE_BASE="${ANDROIDBUILDER_RELEASE_BASE:-https://github.com/$REPO/releases/download}"
BINARIES="aapt2 aidl zipalign split-select"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[ -d "$BUILD_TOOLS_DIR" ] || die "no build-tools at $BUILD_TOOLS_DIR. Run 'Install Android SDK' first."
command -v curl >/dev/null 2>&1 || { apt-get update -qq; apt-get install -y -qq curl; }

# ── 1. the arm64 releases that exist ─────────────────────────────────────────────
# Upstream publishes one release per build-tools revision, but not every revision has one:
# there is no platform-tools-35.0.0, only 35.0.1. A 35.0.x binary drives a 35.0.0 install
# fine, so a missing tag falls back to the newest release of the same major version, and
# only then to the newest release overall.
say "listing the available arm64 build-tools releases"
TAGS_RAW="$(curl -fsSL --retry 3 --max-time 90 "https://api.github.com/repos/$REPO/releases?per_page=100" \
  | sed -n 's/.*"tag_name": *"platform-tools-\([^"]*\)".*/\1/p')" \
  || die "could not reach the GitHub API to list the arm64 releases"
[ -n "$TAGS_RAW" ] || die "no platform-tools-* release found in $REPO"

newest_of() { printf '%s\n' "$TAGS_RAW" | sort -Vr | head -n1; }

resolve_tag() {
  version="$1"
  if printf '%s\n' "$TAGS_RAW" | grep -qxF "$version"; then
    echo "platform-tools-$version"
    return 0
  fi
  local same_major
  same_major="$(printf '%s\n' "$TAGS_RAW" | grep "^${version%%.*}\." | sort -Vr | head -n1)"
  if [ -n "$same_major" ]; then
    warn "$version has no arm64 release; using the $same_major binaries"
    echo "platform-tools-$same_major"
    return 0
  fi
  local any
  any="$(newest_of)"
  warn "$version has no arm64 release, and no other $version%%.* release exists; using $any"
  echo "platform-tools-$any"
}

# ── 2. patch every installed build-tools ────────────────────────────────────────
PATCHED_ANY=0
for dir in "$BUILD_TOOLS_DIR"/*/; do
  [ -d "$dir" ] || continue
  version="$(basename "$dir")"
  if [ -f "$dir/.patched" ]; then
    echo "  $version: already patched"
    continue
  fi
  if [ -n "${ANDROIDBUILDER_PATCHER_TAG:-}" ]; then
    TAG="platform-tools-$ANDROIDBUILDER_PATCHER_TAG"
  else
    TAG="$(resolve_tag "$version")"
  fi

  say "patching build-tools $version for arm64 ($TAG)"
  VERSION_OK=1
  for tool in $BINARIES; do
    printf '  %-13s ... ' "$tool"
    if curl -fsSL --retry 3 --max-time 300 "$RELEASE_BASE/$TAG/$tool" -o "$dir/$tool.tmp"; then
      chmod +x "$dir/$tool.tmp"
      mv "$dir/$tool.tmp" "$dir/$tool"
      echo "ok"
    else
      echo "FAILED"
      rm -f "$dir/$tool.tmp"
      VERSION_OK=0
      break
    fi
  done

  if [ "$VERSION_OK" = 1 ]; then
    if "$dir/aapt2" version >/dev/null 2>&1; then
      touch "$dir/.patched"
      PATCHED_ANY=1
      echo "  $version: done ($("$dir/aapt2" version | head -n1))"
    else
      warn "$version: the downloaded aapt2 does not run on this device, leaving it unpatched"
    fi
  else
    warn "$version: patching failed, leaving it untouched"
  fi
done

[ "$PATCHED_ANY" = 1 ] || echo "  nothing new to patch"

# ── 3. tell Gradle to use the local aapt2 ──────────────────────────────────────
# The glob is sorted, so the last hit is the highest revision: that is the one a new project
# will ask for, and the one the panel reports.
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
  echo "  keep it in sync with the project's buildToolsVersion if that is not $AAPT2"
else
  warn "no aapt2 found under $BUILD_TOOLS_DIR, skipping the gradle.properties override"
fi

say "done"
echo "Next: 'Build debug APK' runs ./gradlew assembleDebug for you."
