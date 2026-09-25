#!/usr/bin/env bash
# AndroidBuilder for ir - OpenJDK 17 inside the proot Debian; ./gradlew cannot start without it.
set -e

JDK_PACKAGE="${ANDROIDBUILDER_JDK_PACKAGE:-openjdk-17-jdk-headless}"
APT_MIRROR="${ANDROIDBUILDER_APT_MIRROR:-}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

java_major() {
  java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n1
}

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
  if [ -n "$APT_MIRROR" ]; then
    say "switching apt to $APT_MIRROR"
    if [ -f /etc/apt/sources.list ]; then
      cp /etc/apt/sources.list "/etc/apt/sources.list.androidbuilder.bak"
      sed -i "s#https\\?://[^ ]*debian[^ ]*#${APT_MIRROR}#g" /etc/apt/sources.list || true
    fi
  fi
  apt-get update
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
