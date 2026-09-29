#!/usr/bin/env bash
# AndroidBuilder for ir - OpenJDK 17 inside the proot Debian; ./gradlew cannot start without it.
#
# The JDK itself comes from apt, so the only question is which archive apt should talk to. Debian's
# own servers are asked first: on a network that cannot reach them they answer 403/404 (or not at
# all), and then one of the regional mirrors is picked - the first that answers, not a hard coded
# one, since a mirror that is blocked here may be perfectly fine elsewhere.
set -e

JDK_PACKAGE="${ANDROIDBUILDER_JDK_PACKAGE:-openjdk-17-jdk-headless}"
APT_MIRROR="${ANDROIDBUILDER_APT_MIRROR:-}"
DEBIAN_PROBE="${ANDROIDBUILDER_DEBIAN_PROBE:-https://deb.debian.org/debian/dists/bookworm/Release}"
MIRROR_CANDIDATES="${ANDROIDBUILDER_APT_MIRRORS:-https://mirrors.aliyun.com/debian https://mirrors.cloud.tencent.com/debian}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# shellcheck source=net-route.sh
[ -f "$SCRIPT_DIR/net-route.sh" ] && . "$SCRIPT_DIR/net-route.sh"

# ── apt sources rewrite ───────────────────────────────────────────────────────
# The security suite does NOT live under the regular archive root: deb.debian.org serves
# bookworm-security from /debian-security, and every regional mirror follows the same split
# (mirrors.aliyun.com/debian-security, mirrors.cloud.tencent.com/debian-security, ...).
# So a mirror swap has to move the "-security" suites to <mirror>-security as well. Rewriting
# every debian URL to the plain mirror base silently repoints them at
# <mirror>/dists/bookworm-security, which is a 404, and then apt-get update fails as a whole
# and not a single package can be installed.
ab_rewrite_apt_sources() {
  base="${1%/}"
  file=/etc/apt/sources.list
  tmp="$file.androidbuilder.tmp"
  awk -v base="$base" '
    # comments and blank lines are copied through untouched
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { print; next }
    {
      is_security = ($0 ~ /-security/)
      line = $0
      n = split(line, tok, /[[:space:]]+/)
      for (i = 1; i <= n; i++) {
        if (tok[i] ~ /^(https?|ftp):\/\//) {
          uri = tok[i]
          sub(/\/+$/, "", uri)
          tok[i] = is_security ? base "-security" : base
          break
        }
      }
      out = ""
      for (i = 1; i <= n; i++) out = out (out == "" ? "" : " ") tok[i]
      if (out != "") print out
    }
  ' "$file" > "$tmp" && mv "$tmp" "$file" || { rm -f "$tmp"; return 1; }
  say "apt sources now:"
  sed -n 's/^deb /  /p' "$file" || true
}

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
