#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. Check if already installed
if command -v intelephense &>/dev/null; then
  echo "intelephense already installed - skipping."
  SKIP=1
else
  SKIP=0
fi

# 1. Install npm (Ghost IDE ships Node.js) - only if missing
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Install intelephense (PHP language server)
if [ "$SKIP" -eq 0 ]; then
  npm install -g intelephense
fi

# 3. Wrapper launcher
cat > /usr/local/bin/php-language-server <<'WRAPPER'
#!/usr/bin/env bash
IP=$(command -v intelephense 2>/dev/null)
if [ -z "$IP" ]; then
  for c in /usr/bin/intelephense /usr/local/bin/intelephense /usr/local/lib/node_modules/intelephense/bin/intelephense.js; do
    [ -e "$c" ] && IP="$c" && break
  done
fi
if [ -z "$IP" ]; then
  echo 'php-language-server: intelephense not found' >&2
  exit 1
fi
exec "$IP" --stdio
WRAPPER
chmod +x /usr/local/bin/php-language-server

echo 'PHP LSP installed.'