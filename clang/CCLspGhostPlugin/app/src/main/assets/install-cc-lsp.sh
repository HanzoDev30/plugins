#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

echo "=== C/C++ LSP (clangd) Installer ==="

# ── 0. Check if already installed ─────────────────────────────────────────
if command -v clangd &>/dev/null; then
  echo "clangd already installed ($(clangd --version)) - skipping."
  SKIP=1
else
  SKIP=0
fi

# ── 1. Install clangd ────────────────────────────────────────────────────
if [ "$SKIP" -eq 0 ]; then
  echo "Installing clangd..."
  apt update && apt install -y clangd
fi

# ── 2. Create wrapper launcher ────────────────────────────────────────────
cat > /usr/local/bin/c-language-server <<'WRAPPER'
#!/usr/bin/env bash
CLANGD_BIN=$(command -v clangd 2>/dev/null)
if [ -z "$CLANGD_BIN" ]; then
  for c in /usr/bin/clangd /usr/local/bin/clangd; do
    [ -x "$c" ] && CLANGD_BIN="$c" && break
  done
fi
if [ -z "$CLANGD_BIN" ]; then
  echo 'c-language-server: clangd not found' >&2
  exit 1
fi
export PATH="$(dirname "$CLANGD_BIN"):$PATH"
exec "$CLANGD_BIN" --header-insertion=never --pch-storage=memory --background-index
WRAPPER
chmod +x /usr/local/bin/c-language-server

echo '=== C/C++ LSP (clangd) installation complete ==='
