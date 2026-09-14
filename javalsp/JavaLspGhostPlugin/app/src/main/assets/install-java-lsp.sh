#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

JDTLS_INSTALL_DIR="/root/jdtls"

# ── 0. If jdtls is already installed, do nothing ────────────────────────────
find_jdtls() {
  for d in \
    "$HOME/jdtls" \
    /root/jdtls \
    /home/*/jdtls \
    /opt/jdtls \
    /usr/local/jdtls \
    /usr/lib/jdtls; do
    if [ -f "$d/bin/jdtls" ] || [ -f "$d/bin/jdtls.py" ]; then
      echo "$d"
      return 0
    fi
  done
  BIN=$(command -v jdtls 2>/dev/null || command -v jdtls.py 2>/dev/null || true)
  if [ -n "$BIN" ]; then
    echo "$(cd "$(dirname "$BIN")/.." && pwd)"
    return 0
  fi
  return 1
}

JDTLS_DIR=$(find_jdtls) || true
if [ -n "$JDTLS_DIR" ]; then
  JDTLS_DIR=$(readlink -f "$JDTLS_DIR")
  echo "jdtls already installed at ${JDTLS_DIR} - skipping installation."
  SKIP=1
else
  JDTLS_DIR="$JDTLS_INSTALL_DIR"
  SKIP=0
fi

# ── 1. Install Java (Temurin 25) via Adoptium ──────────────────────────────
if [ "$SKIP" -eq 0 ]; then
  apt install -y gnupg
  apt update && apt install -y wget gpg ca-certificates

  mkdir -p /etc/apt/keyrings
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg

  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" > /etc/apt/sources.list.d/adoptium.list

  apt update
  apt install -y temurin-25-jdk

  java -version
  javac -version
fi

# ── 2. Download and extract jdtls ──────────────────────────────────────────
if [ "$SKIP" -eq 0 ]; then
  mkdir -p "$JDTLS_DIR"
  cd "$JDTLS_DIR"
  wget -q http://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz -O jdtls.tar.gz
  tar -xzf jdtls.tar.gz
  rm jdtls.tar.gz
  chmod +x "$JDTLS_DIR/bin/jdtls"
  ls "$JDTLS_DIR/bin/jdtls"
fi

# ── 3. Wrapper launcher ────────────────────────────────────────────────────
# Stable entry point for the plugin provider: forwards to $JDTLS_DIR/bin/jdtls --stdio
cat > /usr/local/bin/java-language-server <<'WRAPPER'
#!/usr/bin/env bash
JAVA_BIN=$(command -v java 2>/dev/null)
if [ -z "$JAVA_BIN" ]; then
  for c in /usr/bin/java /usr/local/bin/java; do
    [ -x "$c" ] && JAVA_BIN="$c" && break
  done
fi
if [ -z "$JAVA_BIN" ]; then
  echo 'java-language-server: java not found' >&2
  exit 1
fi
export PATH="$(dirname "$JAVA_BIN"):$PATH"

JDTLS="__JDTLS_DIR__/bin/jdtls"
[ -x "$JDTLS" ] || { echo "java-language-server: launcher not found at $JDTLS" >&2; exit 1; }
exec "$JDTLS" --stdio
WRAPPER
sed -i "s|__JDTLS_DIR__|${JDTLS_DIR}|g" /usr/local/bin/java-language-server
chmod +x /usr/local/bin/java-language-server

echo 'Installation complete.'