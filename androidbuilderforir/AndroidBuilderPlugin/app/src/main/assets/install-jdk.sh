#!/usr/bin/env bash
# AndroidBuilder for ir - OpenJDK 17 inside the proot Debian; ./gradlew cannot start without it.
#
# The JDK comes straight from apt, which talks to Debian's own archive (deb.debian.org).
# No regional mirror swapping: the user's Debian setup is fast and reliable as it is.
set -e

JDK_PACKAGE="${ANDROIDBUILDER_JDK_PACKAGE:-openjdk-17-jdk-headless}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

java_major() {
  java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n1
}

# ── 0. repair a broken security URL before anything apt-related runs ───────────
# bookworm-security lives under /debian-security, not /debian. If a previous mirror
# rewrite (or an old reference) left <archive>/debian bookworm-security behind, apt
# 404s on it and the whole update fails. Normalize it before updating.
if [ -f /etc/apt/sources.list ]; then
  if grep -qE '(^|[[:space:]])deb .*bookworm-security' /etc/apt/sources.list 2>/dev/null \
     && ! grep -qE 'debian-security.*bookworm-security' /etc/apt/sources.list 2>/dev/null; then
    say "repairing broken bookworm-security URL in /etc/apt/sources.list"
    cp -f /etc/apt/sources.list /etc/apt/sources.list.androidbuilder.bak 2>/dev/null || true
    sed -i -E 's#(https?://[^ ]+/debian)([[:space:]]+bookworm-security)#\1-security\2#g' /etc/apt/sources.list \
      || warn "sed repair failed; continuing anyway"
  fi
fi

# ── 1. decide whether anything has to be installed ──────────────────────────────
NEED_INSTALL=1
if command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  MAJOR="$(java_major)"
  if [ -n "$MAJOR" ] && [ "$MAJOR" -ge 17 ] 2>/dev/null; then
    NEED_INSTALL=0
    say "java $(java -version 2>&1 | head -n1) is already installed"
  else
    warn "java $MAJOR is older than 17; Gradle 8 needs 17, so $JDK_PACKAGE will be installed"
  fi
elif command -v java >/dev/null 2>&1; then
  warn "only a JRE is present (no javac); $JDK_PACKAGE will be installed"
else
  say "no JVM found, installing $JDK_PACKAGE"
fi

# ── 2. install when needed ──────────────────────────────────────────────────────
if [ "$NEED_INSTALL" = 1 ]; then
  if [ -z "$APT_MIRROR" ] && command -v ab_route >/dev/null 2>&1; then
    say "asking deb.debian.org whether it answers from here"
    DEBIAN_ROUTE="$(ab_route "$DEBIAN_PROBE" '^Package:')"
    case "$DEBIAN_ROUTE" in
      direct*)
        say "deb.debian.org answers normally here ($DEBIAN_ROUTE): apt keeps its own archive"
        ;;
      *)
        warn "deb.debian.org is not usable here ($DEBIAN_ROUTE)"
        APT_MIRROR="$(ab_route_pick $MIRROR_CANDIDATES || true)"
        if [ -n "$APT_MIRROR" ]; then
          warn "using $APT_MIRROR instead"
        else
          APT_MIRROR="${MIRROR_CANDIDATES%% *}"
          warn "none of the regional mirrors answered either; trying $APT_MIRROR anyway"
        fi
        ;;
    esac
  fi

  if [ -n "$APT_MIRROR" ]; then
    say "switching apt to $APT_MIRROR"
    if [ -f /etc/apt/sources.list ]; then
      cp /etc/apt/sources.list "/etc/apt/sources.list.androidbuilder.bak"
      ab_rewrite_apt_sources "$APT_MIRROR"
    fi
    ab_rewrite_sources_d "$APT_MIRROR"
  fi

  # apt update may still fail on a stale nodesource/other repo; do not abort the JDK install
  # because of an unrelated repository. Try to update, warn on failure, and continue.
  if ! apt-get update; then
    warn "apt-get update returned a non-zero status; continuing with what is cached"
  fi

  apt-get install -y --no-install-recommends "$JDK_PACKAGE"
  hash -r
fi

command -v java >/dev/null 2>&1 || die "java is still not available after install"
command -v javac >/dev/null 2>&1 || die "javac is still not available after install"

# ── 3. JAVA_HOME for the login shell ────────────────────────────────────────────
JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
say "JAVA_HOME=$JAVA_HOME"
cat > /etc/profile.d/androidbuilder-java.sh <<EOF
# AndroidBuilder for ir
export JAVA_HOME="$JAVA_HOME"
export PATH="\$PATH:\$JAVA_HOME/bin"
EOF
chmod +x /etc/profile.d/androidbuilder-java.sh

for rc in "$HOME/.bashrc" "$HOME/.bash_profile"; do
  touch "$rc"
  if ! grep -q 'androidbuilder-for-ir-java' "$rc"; then
    {
      printf '\n# >>> androidbuilder-for-ir-java >>>\n'
      printf 'export JAVA_HOME="%s"\n' "$JAVA_HOME"
      printf 'export PATH="$PATH:$JAVA_HOME/bin"\n'
    } >> "$rc"
  fi
done

say "done"
java -version
javac -version
echo "Open a new terminal tab to pick up JAVA_HOME."