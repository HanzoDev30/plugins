#!/usr/bin/env bash
# AndroidBuilder for ir - install the Android SDK from the Iranian mirror maven.myket.ir.
# Nothing here touches dl.google.com / dl-ssl.google.com.
#
#   install-sdk.sh            # platform-tools + build-tools + platforms + licenses (~140 MB)
#   install-sdk.sh core       # only platform-tools + licenses
#   install-sdk.sh tools      # the command line tools (sdkmanager, 130 MB) - rarely needed
#   install-sdk.sh build-tools
#   install-sdk.sh platform
#   install-sdk.sh sources    # the sources jars of the installed platforms
#   install-sdk.sh ndk        # OPTIONAL - the NDK is not needed for Kotlin/Java apps
#   install-sdk.sh cmake      # OPTIONAL - only for projects that build native code
#   install-sdk.sh all        # everything, including the optional NDK and CMake
set -e

COMPONENT="${1:-default}"
MIRROR="${ANDROIDBUILDER_MIRROR:-https://maven.myket.ir/android-sdk}"
CSV_URL="${ANDROIDBUILDER_CSV:-https://maven.myket.ir/sdk-archives.csv}"
CMDLINE_ZIP="commandlinetools-linux-14742923_latest.zip"
CMDLINE_SHA1="48833c34b761c10cb20bcd16582129395d121b27"
BUILD_TOOLS="${ANDROIDBUILDER_BUILD_TOOLS:-37.0.0}"
PLATFORMS="${ANDROIDBUILDER_PLATFORMS:-android-36}"
NDK_VERSIONS="${ANDROIDBUILDER_NDK:-27.3.13750724 28.2.13676358 29.0.14206865}"
CMAKE_VERSIONS="${ANDROIDBUILDER_CMAKE:-3.22.1 3.31.6}"
SDK_DIR="${ANDROID_HOME:-$HOME/Android/sdk}"
WORK_DIR="${ANDROIDBUILDER_TMP:-/tmp/androidbuilder-sdk}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

mkdir -p "$WORK_DIR"

# ── prerequisites ───────────────────────────────────────────────────────────────
command -v java >/dev/null 2>&1 \
  || die "no JVM found. Run the 'Install JDK 17' button from the AndroidBuilder panel first."
command -v curl >/dev/null 2>&1 || { say "installing curl"; apt-get update -qq; apt-get install -y -qq curl; }
command -v unzip >/dev/null 2>&1 || { say "installing unzip"; apt-get install -y -qq unzip; }
command -v sha1sum >/dev/null 2>&1 || { say "installing coreutils"; apt-get install -y -qq coreutils; }

# ── mirror index: url + sha1 for a package, straight from the mirror's own csv ──
INDEX="$WORK_DIR/sdk-archives.csv"
if [ ! -s "$INDEX" ]; then
  say "downloading the mirror index ($CSV_URL)"
  curl -fsSL --retry 3 --retry-delay 2 -o "$INDEX" "$CSV_URL" \
    || die "could not download $CSV_URL"
fi

# lookup <package> [prefer-regex] -> prints "<url> <sha1> <bytes>"
# The linux row is preferred, but platforms/sources have one platform neutral archive, and the
# platform archives come in an "ext" flavour that a build does not want.
lookup() {
  awk -F',' -v pkg="$1" -v pref="${2:-}" '
    $1 == pkg && $4 !~ /darwin|windows|macosx/ {
      count++
      url[count] = $4
      sha[count] = $6
      size[count] = $5
      if (pref != "" && $4 ~ pref) { print $4 " " $6 " " $5; picked = 1; exit }
      if ($4 ~ /linux/ && !linux) { linux = 1; linuxIndex = count }
    }
    END {
      if (picked) exit
      if (linux) { print url[linuxIndex] " " sha[linuxIndex] " " size[linuxIndex]; exit }
      if (count > 0) { print url[1] " " sha[1] " " size[1] }
    }
  ' "$INDEX"
}

human() {
  local bytes="${1:-0}"
  awk -v b="$bytes" 'BEGIN {
    if (b == "" || b + 0 <= 0) { print "?"; exit }
    printf "%.0f MB", b / 1048576
  }'
}

# fetch <package> <prefer-regex> <destination-zip>
fetch() {
  local package="$1" prefer="${2:-}" target="$3" entry url sha1 size rest
  entry="$(lookup "$package" "$prefer")"
  [ -n "$entry" ] || die "'$package' is not in the mirror index $CSV_URL"
  url="${entry%% *}"
  rest="${entry#* }"
  sha1="${rest%% *}"
  size="${rest##* }"
  say "downloading $package ($(human "$size")) from $url"
  curl -fL --retry 3 --retry-delay 2 -o "$target" "$url" \
    || die "download failed: $url"
  printf '%s  %s\n' "$sha1" "$target" | sha1sum -c - >/dev/null 2>&1 \
    || die "sha1 mismatch for $url (the mirror sent a corrupted or truncated file)"
  echo "  sha1 ok: $sha1"
}

# install_zip <package> <prefer-regex> <target-directory>
# Most Google archives wrap the package in one folder (android-36/, android-16/, platform-tools/,
# android-ndk-r27d/, cmdline-tools/), while cmake puts bin/, share/ and source.properties straight
# into the archive root. Both layouts are handled here.
install_zip() {
  local package="$1" prefer="${2:-}" target="$3" zip extract top
  if [ -f "$target/source.properties" ]; then
    say "$package is already installed"
    return 0
  fi
  zip="$WORK_DIR/$(echo "$package" | tr ';/' '__').zip"
  extract="$WORK_DIR/extract"
  fetch "$package" "$prefer" "$zip"
  say "unpacking $package -> $target"
  rm -rf "$extract"
  mkdir -p "$extract"
  unzip -q "$zip" -d "$extract"
  if [ -f "$extract/source.properties" ] || [ -d "$extract/bin" ]; then
    top="$extract"
  else
    top="$(find "$extract" -mindepth 1 -maxdepth 1 -type d | head -n1)"
  fi
  [ -n "$top" ] || die "$package did not contain a package directory"
  mkdir -p "$(dirname "$target")"
  rm -rf "$target"
  mkdir -p "$target"
  cp -R "$top"/. "$target"/
  rm -rf "$extract" "$zip"
}

# ── 1. sdk layout ───────────────────────────────────────────────────────────────
say "SDK directory: $SDK_DIR"
mkdir -p "$SDK_DIR/cmdline-tools"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

# Only the components that need sdkmanager also pull in the command line tools and the
# environment exports; the other components just drop their own archive into place.
case "$COMPONENT" in
  core | default | all) NEEDS_CORE=1 ;;
  *) NEEDS_CORE=0 ;;
esac

if [ "$NEEDS_CORE" = 1 ] || [ "$COMPONENT" = "tools" ]; then
  # ── 2. command line tools (only when asked for) + platform-tools ───────────────
  if [ "$COMPONENT" = "tools" ]; then
    install_zip "cmdline-tools;latest" '' "$SDK_DIR/cmdline-tools/latest"
  elif [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    say "skipping the command line tools (130 MB); run '$0 tools' if you ever need sdkmanager"
  fi
  if [ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    export PATH="$PATH:$SDK_DIR/cmdline-tools/latest/bin"
  fi
  export PATH="$PATH:$SDK_DIR/platform-tools"

  if [ -d "$SDK_DIR/platform-tools" ]; then
    say "platform-tools already installed"
  else
    install_zip "platform-tools" '' "$SDK_DIR/platform-tools"
  fi

  # ── 3. environment for the login shell (terminal sessions are 'bash --login') ─
  say "registering ANDROID_HOME / ANDROID_SDK_ROOT / PATH"
  cat > /etc/profile.d/androidbuilder-env.sh <<EOF
# AndroidBuilder for ir
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
EOF
  chmod +x /etc/profile.d/androidbuilder-env.sh

  for rc in "$HOME/.bashrc" "$HOME/.bash_profile"; do
    touch "$rc"
    if ! grep -q 'androidbuilder-for-ir' "$rc"; then
      {
        printf '\n# >>> androidbuilder-for-ir >>>\n'
        printf 'export ANDROID_HOME="%s"\n' "$SDK_DIR"
        printf 'export ANDROID_SDK_ROOT="$ANDROID_HOME"\n'
        printf 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"\n'
      } >> "$rc"
    fi
  done

  # ── 4. licenses, written straight into the SDK (no dl.google.com round trip) ──
  say "writing SDK license acceptances"
  mkdir -p "$SDK_DIR/licenses"
  for license in android-sdk-license android-sdk-preview-license android-sdk-arm-dbt-license \
                android-googletv-license google-gdk-license intel-android-extra-license \
                mips-android-sysimage-license; do
    cat > "$SDK_DIR/licenses/$license" <<'HASHES'
24333f8a63b6825ea9c5514f83c2829b004d1fee
d56f5187479451eabf01fb78af6dfcb131a6481e
8933bad161af4178b1185d1a37fbf41ea5269c55
HASHES
  done
fi

# ── 5. the requested component ──────────────────────────────────────────────────
case "$COMPONENT" in
  core)
    say "platform-tools and the licenses are ready"
    ;;
  tools)
    say "command line tools (sdkmanager) are ready"
    ;;
  default|all)
    if [ "$COMPONENT" = "all" ]; then
      for V in $BUILD_TOOLS; do install_zip "build-tools;$V" '' "$SDK_DIR/build-tools/$V"; done
      for P in $PLATFORMS; do
        install_zip "platforms;$P" "platform-${P#android-}_r" "$SDK_DIR/platforms/$P"
      done
      "$0" ndk
      "$0" cmake
    else
      # Deliberately not sdkmanager: it resolves everything through dl.google.com, which hangs
      # without a route. Every archive comes straight from the mirror instead.
      for V in $BUILD_TOOLS; do install_zip "build-tools;$V" '' "$SDK_DIR/build-tools/$V"; done
      for P in $PLATFORMS; do
        install_zip "platforms;$P" "platform-${P#android-}_r" "$SDK_DIR/platforms/$P"
      done
    fi
    ;;
  build-tools)
    for V in $BUILD_TOOLS; do install_zip "build-tools;$V" '' "$SDK_DIR/build-tools/$V"; done
    ;;
  platform)
    for P in $PLATFORMS; do
      install_zip "platforms;$P" "platform-${P#android-}_r" "$SDK_DIR/platforms/$P"
    done
    ;;
  sources)
    for P in $PLATFORMS; do
      if [ -d "$SDK_DIR/platforms/$P" ]; then
        install_zip "sources;$P" "source-${P#android-}_r" "$SDK_DIR/sources/$P" \
          || warn "no sources package for $P"
      else
        warn "$P is not installed, skipping its sources"
      fi
    done
    ;;
  ndk)
    say "the NDK is optional - pure Kotlin/Java projects do not need it"
    if [ -n "${ANDROIDBUILDER_NDK_URL:-}" ]; then
      # An aarch64 (real ARM phone) NDK, for example the prebuilt ones from
      # https://github.com/HomuHomu833/android-ndk-custom/releases - needed because the mirror only
      # ships Google's x86_64 Linux NDK, which cannot run on an arm64 device.
      say "installing the NDK from ANDROIDBUILDER_NDK_URL"
      ARCHIVE="$WORK_DIR/ndk-$(basename "${ANDROIDBUILDER_NDK_URL}")"
      curl -fL --retry 3 --retry-delay 2 -o "$ARCHIVE" "$ANDROIDBUILDER_NDK_URL" \
        || die "download failed: $ANDROIDBUILDER_NDK_URL"
      [ -n "${ANDROIDBUILDER_NDK_SHA1:-}" ] \
        && { printf '%s  %s\n' "$ANDROIDBUILDER_NDK_SHA1" "$ARCHIVE" | sha1sum -c - >/dev/null \
             || die "sha1 mismatch for $ANDROIDBUILDER_NDK_URL"; }
      VERSION="${ANDROIDBUILDER_NDK_VERSION:-custom}"
      EXTRACT="$WORK_DIR/ndk-extract"
      rm -rf "$EXTRACT"; mkdir -p "$EXTRACT"
      case "$ARCHIVE" in
        *.tar.xz|*.tar.gz|*.tgz) tar -xf "$ARCHIVE" -C "$EXTRACT" ;;
        *) unzip -q "$ARCHIVE" -d "$EXTRACT" ;;
      esac
      TOP="$(find "$EXTRACT" -mindepth 1 -maxdepth 1 -type d | head -n1)"
      [ -n "$TOP" ] || die "the NDK archive did not contain a directory"
      rm -rf "$SDK_DIR/ndk/$VERSION"
      mkdir -p "$SDK_DIR/ndk"
      mv "$TOP" "$SDK_DIR/ndk/$VERSION"
      rm -rf "$EXTRACT" "$ARCHIVE"
      echo "  NDK $VERSION installed from the custom aarch64 archive"
    else
      warn "the mirror only ships Google's x86_64 Linux NDK, which cannot run on an arm64 phone."
      echo "  For native projects set ANDROIDBUILDER_NDK_URL to an aarch64 NDK archive, e.g."
      echo "  export ANDROIDBUILDER_NDK_URL=https://github.com/HomuHomu833/android-ndk-custom/releases/download/r27/android-ndk-r27d-aarch64-linux-gnu.tar.xz"
      for V in $NDK_VERSIONS; do
        case "$V" in
          custom) continue ;;
        esac
        install_zip "ndk;$V" '' "$SDK_DIR/ndk/$V"
      done
    fi
    ;;
  cmake)
    for V in $CMAKE_VERSIONS; do install_zip "cmake;$V" '' "$SDK_DIR/cmake/$V"; done
    ;;
  *)
    die "unknown component '$COMPONENT' (core, default, tools, build-tools, platform, sources, ndk, cmake, all)"
    ;;
esac

# ── 6. summary ──────────────────────────────────────────────────────────────────
say "done ($COMPONENT)"
echo "  SDK root      : $SDK_DIR"
echo "  build-tools   : $(ls -1 "$SDK_DIR/build-tools" 2>/dev/null | tr '\n' ' ')"
echo "  platforms     : $(ls -1 "$SDK_DIR/platforms" 2>/dev/null | tr '\n' ' ')"
echo "  platform-tools: $([ -d "$SDK_DIR/platform-tools" ] && echo yes || echo no)"
echo "  ndk (optional): $(ls -1 "$SDK_DIR/ndk" 2>/dev/null | tr '\n' ' ')"
echo "  cmake (opt.)  : $(ls -1 "$SDK_DIR/cmake" 2>/dev/null | tr '\n' ' ')"
echo "  cmdline-tools: $([ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ] && echo yes || echo 'no (optional)')"
echo
echo "Next: press 'Configure Gradle (Iran mirror)' and 'Patch build-tools for ARM64' in the"
echo "AndroidBuilder panel, then 'Build debug APK'."
echo "Open a new terminal tab (or run 'source ~/.bashrc') to pick up the new PATH."
