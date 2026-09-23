#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Install Node.js and npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Install / repair vscode-langservers-extracted globally.
#    --force makes npm re-extract every file, which also fixes any previous
#    botched install that overwrote files inside the package.
npm install -g --force @t1ckbase/vscode-langservers-extracted

# 3. Resolve the real JSON server entry. Never write inside the npm package dir.
ENTRY="$(npm root -g)/@t1ckbase/vscode-langservers-extracted/bin/json.js"
if [ ! -f "$ENTRY" ]; then
  for c in /usr/lib/node_modules/@t1ckbase/vscode-langservers-extracted/bin/json.js \
           /usr/local/lib/node_modules/@t1ckbase/vscode-langservers-extracted/bin/json.js; do
    [ -f "$c" ] && ENTRY="$c" && break
  done
fi
if [ ! -f "$ENTRY" ]; then
  echo 'json-language-server: vscode-json-language-server entry not found' >&2
  exit 1
fi

# 4. Wrapper launcher (stdio transport is required)
mkdir -p /usr/local/bin
cat > /usr/local/bin/json-language-server <<WRAPPER
#!/usr/bin/env bash
exec node "$ENTRY" --stdio "\$@"
WRAPPER
chmod +x /usr/local/bin/json-language-server

echo 'JSON LSP installed.'