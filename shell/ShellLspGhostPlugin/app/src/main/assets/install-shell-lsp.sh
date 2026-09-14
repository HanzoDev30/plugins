#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Install Node.js and npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Install bash-language-server
npm install -g bash-language-server

# 3. Wrapper launcher
cat > /usr/local/bin/shell-language-server <<'WRAPPER'
#!/usr/bin/env bash
BLS=$(command -v bash-language-server 2>/dev/null)
if [ -z "$BLS" ]; then
  for c in /usr/bin/bash-language-server /usr/local/bin/bash-language-server /usr/local/lib/node_modules/bash-language-server/bin/main.js; do
    [ -x "$c" ] && BLS="$c" && break
    [ -f "$c" ] && BLS="$c" && break
  done
fi
if [ -z "$BLS" ]; then
  echo 'shell-language-server: bash-language-server not found' >&2
  exit 1
fi
exec "$BLS" start
WRAPPER
chmod +x /usr/local/bin/shell-language-server

echo 'Shell LSP installed.'