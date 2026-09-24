#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. prerequisites
if ! command -v curl &>/dev/null || ! command -v unzip &>/dev/null; then
  apt update
fi
apt install -y curl unzip

# 1. PowerShell (pwsh) from the Microsoft repo
apt install -y ca-certificates gnupg apt-transport-https
if ! command -v pwsh &>/dev/null; then
  curl -sSL https://packages.microsoft.com/config/debian/12/packages-microsoft-prod.deb -o /tmp/ms-prod.deb
  apt install -y /tmp/ms-prod.deb
  apt update
fi
apt install -y powershell

# 2. PowerShellEditorServices bundle
PSES_DIR=/opt/powershell-lsp
mkdir -p "$PSES_DIR"
if [ ! -f "$PSES_DIR/PowerShellEditorServices/Start-EditorServices.ps1" ]; then
  curl -sSL https://github.com/PowerShell/PowerShellEditorServices/releases/latest/download/PowerShellEditorServices.zip -o /tmp/pses.zip
  rm -rf "$PSES_DIR/PowerShellEditorServices"
  unzip -o /tmp/pses.zip -d "$PSES_DIR"
fi

# 3. Wrapper launcher: pure stdio LSP over a single pwsh process
cat > /usr/local/bin/powershell-language-server <<'WRAPPER'
#!/usr/bin/env bash
export PATH="/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"
export HOME="${HOME:-/root}"
PSES_PS1=/opt/powershell-lsp/PowerShellEditorServices/Start-EditorServices.ps1

if [ ! -f "$PSES_PS1" ]; then
  echo 'powershell-language-server: PSES bundle missing. Re-run the installer.' >&2
  exit 1
fi
if ! command -v pwsh >/dev/null 2>&1; then
  echo 'powershell-language-server: pwsh not found. Re-run the installer.' >&2
  exit 1
fi

export PSModulePath="/opt/powershell-lsp/PowerShellEditorServices${PSModulePath:+:$PSModulePath}"

exec pwsh -NoLogo -NoProfile -NonInteractive -Command "
  \$ProgressPreference = 'SilentlyContinue'
  & '$PSES_PS1' -BundledModulesPath '/opt/powershell-lsp/PowerShellEditorServices' \
    -LogPath '/tmp/powershell-lsp.log' -LogLevel Normal \
    -HostName 'Ghost IDE' -HostProfileId 'ghostide' -HostVersion 1.0.0 -Stdio
"
WRAPPER
chmod +x /usr/local/bin/powershell-language-server

# 4. Self-test: send an LSP initialize frame and verify a valid response
echo 'Self-testing PowerShell LSP over stdio (may take up to 60s)...'
PAYLOAD='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}}'
LEN=${#PAYLOAD}
RESP=$(printf 'Content-Length: %d\r\n\r\n%s' "$LEN" "$PAYLOAD" | timeout 75 /usr/local/bin/powershell-language-server 2>/tmp/pses.install.err || true)

case "$RESP" in
  *'"capabilities"'*)
    echo 'OK: PowerShell LSP answered initialize on stdio.'
    ;;
  *)
    echo 'FAILED: no valid LSP response. Showing the real cause:'
    echo '--- /tmp/powershell-lsp.log (tail) ---'
    tail -40 /tmp/powershell-lsp.log 2>/dev/null || true
    echo '--- stderr (tail) ---'
    tail -20 /tmp/pses.install.err 2>/dev/null || true
    ;;
esac