#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

echo "=== ty installer (astral-sh/ty) ==="

# ── 0. Already installed? ────────────────────────────────────────────────
if command -v ty &>/dev/null && ty --version &>/dev/null; then
  echo "ty already installed: $(ty --version 2>/dev/null) - skipping."
  SKIP=1
else
  SKIP=0
fi

# ── 1. Bootstrap python3 + pip ───────────────────────────────────────────
if [ "$SKIP" -eq 0 ]; then
  if ! command -v python3 &>/dev/null; then
    echo "Installing python3 and pip..."
    apt update && apt install -y python3 python3-pip
  fi
  if ! command -v pip &>/dev/null && ! command -v pip3 &>/dev/null; then
    apt install -y python3-pip || true
  fi
fi

# ── 2. Install ty via pip (preferred, pure prebuilt wheel) ──────────────
if [ "$SKIP" -eq 0 ]; then
  echo "Installing ty with pip..."
  if command -v pip &>/dev/null; then
    pip install -U ty --break-system-packages || pip install -U ty
  else
    pip3 install -U ty --break-system-packages || pip3 install -U ty
  fi
fi

# ── 3. Fallback: official standalone installer ───────────────────────────
if ! command -v ty &>/dev/null; then
  echo "pip route failed, trying the official astral installer..."
  curl -LsSf https://astral.sh/ty/install.sh | sh || true
fi

# ── 4. Wrapper launcher used by the editor ───────────────────────────────
cat > /usr/local/bin/ty-language-server <<'WRAPPER'
#!/usr/bin/env bash
TY_BIN=$(command -v ty 2>/dev/null)
if [ -z "$TY_BIN" ]; then
  for c in /usr/local/bin/ty /usr/bin/ty /root/.local/bin/ty "${HOME:-/root}/.local/bin/ty" /root/.cargo/bin/ty; do
    [ -x "$c" ] && TY_BIN="$c" && break
  done
fi
if [ -z "$TY_BIN" ]; then
  echo 'ty-language-server: ty binary not found' >&2
  exit 1
fi
exec "$TY_BIN" server
WRAPPER
chmod +x /usr/local/bin/ty-language-server

# ── 5. Sanity check ──────────────────────────────────────────────────────
if command -v ty &>/dev/null; then
  echo "ty version: $(ty --version 2>/dev/null || echo unknown)"
else
  echo "WARNING: ty is still not on PATH, check the install log above." >&2
fi

echo '=== ty installation complete ==='
