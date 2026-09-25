#!/bin/sh
set -eu
export DEBIAN_FRONTEND=noninteractive
export HOME="${HOME:-/root}"
PATH="${PATH:-/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin}"
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/local/sbin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

# 0. prerequisites (always refresh the index — a fresh proot rootfs can have
# curl/tar preinstalled but an empty /var/lib/apt/lists, which made the old
# "only update if missing" check skip apt update and fail on install)
apt update
apt install -y curl tar ca-certificates

# 1. detect architecture
ARCH=$(uname -m)
case "$ARCH" in
  x86_64|amd64)
    AGO_CLI="64bit"
    GO_ARCH="amd64"
    ;;
  aarch64|arm64|armv8*)
    AGO_CLI="ARM64"
    GO_ARCH="arm64"
    ;;
  *)
    echo "arduino-lsp: unsupported architecture: $ARCH" >&2
    exit 1
    ;;
esac

CLI_VERSION="1.0.4"

download_with_fallback() {
  output="$1"
  shift
  for url in "$@"; do
    if curl -fsSL --retry 3 --retry-delay 2 "$url" -o "$output"; then
      return 0
    fi
    printf 'Download failed, trying the next source: %s\n' "$url" >&2
  done
  return 1
}

mkdir -p "$HOME/.arduino15" "$HOME/Arduino" "$HOME/.local/bin"

# 2. arduino-cli
if ! command -v arduino-cli >/dev/null 2>&1 && [ ! -x "$HOME/.local/bin/arduino-cli" ]; then
  echo "Downloading arduino-cli $CLI_VERSION ..."
  curl -fsSL "https://downloads.arduino.cc/arduino-cli/arduino-cli_${CLI_VERSION}_Linux_${AGO_CLI}.tar.gz" -o /tmp/arduino-cli.tar.gz
  tar -xzf /tmp/arduino-cli.tar.gz -C "$HOME/.local/bin" arduino-cli
  chmod +x "$HOME/.local/bin/arduino-cli"
fi
for c in "$HOME/.local/bin/arduino-cli" /root/.local/bin/arduino-cli /usr/local/bin/arduino-cli /usr/bin/arduino-cli; do
  [ -x "$c" ] && CLI="$c" && break
done
CLI="${CLI:-$HOME/.local/bin/arduino-cli}"

# 3. Go toolchain and arduino-language-server
GO_VERSION="1.26.5"
LS_VERSION="latest"
LS_REAL_BIN="/usr/local/lib/arduino-language-server-bin"
GO_ARCHIVE="/tmp/go${GO_VERSION}.tar.gz"

mkdir -p /usr/local/lib
printf '%s\n' "Installing Go $GO_VERSION from the Iranian mirror, then the official source ..."
download_with_fallback "$GO_ARCHIVE" \
  "https://mirror.ismdeep.com/go/dist/go${GO_VERSION}/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" \
  "https://go.dev/dl/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" \
  "https://mirrors.aliyun.com/golang/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz"
rm -rf /usr/local/go
tar -xzf "$GO_ARCHIVE" -C /usr/local
ln -sf /usr/local/go/bin/go /usr/local/bin/go
ln -sf /usr/local/go/bin/gofmt /usr/local/bin/gofmt
export PATH="/usr/local/go/bin:$PATH"
export GOBIN="/usr/local/lib"
export GOTOOLCHAIN=local
export GOSUMDB=off
go version

printf '%s\n' "Installing arduino-language-server $LS_VERSION with Go ..."
if ! GOPROXY="https://mirror.rasanegaar.com/golang,direct" go install "github.com/arduino/arduino-language-server@${LS_VERSION}"; then
  printf '%s\n' "Iranian Go module mirror failed; using the official module proxy ..."
  if ! GOPROXY="https://proxy.golang.org,direct" go install "github.com/arduino/arduino-language-server@${LS_VERSION}"; then
    printf '%s\n' "Official Go module proxy failed; using the fallback proxy ..."
    GOPROXY="https://goproxy.cn,direct" go install "github.com/arduino/arduino-language-server@${LS_VERSION}"
  fi
fi
mv -f "$GOBIN/arduino-language-server" "$LS_REAL_BIN"
chmod +x "$LS_REAL_BIN"

# 4. initialise arduino-cli (config + core index + AVR core so the LSP has symbols)
"$CLI" config init --overwrite || true
"$CLI" core update-index
"$CLI" core install arduino:avr

# 5. Wrapper launcher: pure stdio LSP over arduino-language-server.
# This now execs the renamed real binary (/usr/local/lib/...), not its own
# name — the old version overwrote the real binary at this same path and
# then called itself, causing infinite recursion.
cat > /usr/local/bin/arduino-language-server <<'WRAPPER'
#!/bin/sh
set -eu
export HOME="${HOME:-/root}"
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin${PATH:+:$PATH}"

if [ -x "$HOME/.local/bin/arduino-cli" ]; then
  CLI="$HOME/.local/bin/arduino-cli"
elif command -v arduino-cli >/dev/null 2>&1; then
  CLI="$(command -v arduino-cli)"
else
  echo 'arduino-language-server: arduino-cli not found. Re-run the installer.' >&2
  exit 1
fi

exec /usr/local/lib/arduino-language-server-bin \
  -cli-config "$HOME/.arduino15/arduino-cli.yaml" \
  -cli "$CLI" \
  -fqbn "arduino:avr:uno"
WRAPPER
chmod +x /usr/local/bin/arduino-language-server

# 6. Self-test: send an LSP initialize frame and verify a valid response
echo 'Self-testing Arduino LSP over stdio (may take up to 90s)...'
PAYLOAD='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}}'
LEN=${#PAYLOAD}
RESP=$(printf 'Content-Length: %d\r\n\r\n%s' "$LEN" "$PAYLOAD" | timeout 90 /usr/local/bin/arduino-language-server 2>/tmp/arduino-ls.install.err || true)

case "$RESP" in
  *'"capabilities"'*)
    echo 'OK: Arduino LSP answered initialize on stdio.'
    ;;
  *)
    echo 'FAILED: no valid LSP response. Showing the real cause:'
    echo '--- stderr (tail) ---'
    tail -30 /tmp/arduino-ls.install.err 2>/dev/null || true
    exit 1
    ;;
esac
