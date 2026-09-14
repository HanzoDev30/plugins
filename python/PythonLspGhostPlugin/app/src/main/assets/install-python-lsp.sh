#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

echo "=== Python LSP Installer (Lightweight) ==="

# ── 0. Check if already installed ─────────────────────────────────────────
if command -v python3 &>/dev/null && python3 -c "import pylsp" 2>/dev/null; then
  echo "python-lsp-server already installed - skipping."
  SKIP=1
else
  SKIP=0
fi

# ── 1. Install Python3 and pip ────────────────────────────────────────────
if [ "$SKIP" -eq 0 ]; then
  echo "Installing python3 and pip..."
  apt update && apt install -y python3 python3-pip python3-venv
fi

# ── 2. Install python-lsp-server (lightweight - no "all") ─────────────────
if [ "$SKIP" -eq 0 ]; then
  echo "Installing python-lsp-server (core only)..."
  pip install python-lsp-server --break-system-packages

  echo "Installing ruff (fast linter)..."
  #pip install ruff --break-system-packages
fi

# ── 3. Create wrapper launcher ────────────────────────────────────────────
cat > /usr/local/bin/python-language-server <<'WRAPPER'
#!/usr/bin/env bash
PYTHON_BIN=$(command -v python3 2>/dev/null)
if [ -z "$PYTHON_BIN" ]; then
  for c in /usr/bin/python3 /usr/local/bin/python3; do
    [ -x "$c" ] && PYTHON_BIN="$c" && break
  done
fi
if [ -z "$PYTHON_BIN" ]; then
  echo 'python-language-server: python3 not found' >&2
  exit 1
fi
export PATH="$(dirname "$PYTHON_BIN"):$PATH"
exec "$PYTHON_BIN" -m pylsp --tcp
WRAPPER
chmod +x /usr/local/bin/python-language-server

echo '=== Python LSP installation complete ==='
