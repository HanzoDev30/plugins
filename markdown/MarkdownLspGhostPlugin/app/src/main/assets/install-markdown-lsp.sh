#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Install Node.js and npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Install / repair markdownlint-lsp globally.
#    --force makes npm re-extract every file, which also fixes any previous
#    botched install that overwrote lib/index.mjs inside the package.
npm install -g --force markdownlint-lsp

# 3. Resolve the real server entry. Never write inside the npm package dir.
ENTRY="$(npm root -g)/markdownlint-lsp/lib/index.mjs"
if [ ! -f "$ENTRY" ]; then
  for c in /usr/lib/node_modules/markdownlint-lsp/lib/index.mjs \
           /usr/local/lib/node_modules/markdownlint-lsp/lib/index.mjs; do
    [ -f "$c" ] && ENTRY="$c" && break
  done
fi
if [ ! -f "$ENTRY" ]; then
  echo 'markdownlint-lsp-server: markdownlint-lsp entry not found' >&2
  exit 1
fi

# 4. Wrapper launcher (stdio transport is required)
mkdir -p /usr/local/bin
cat > /usr/local/bin/markdownlint-lsp-server <<WRAPPER
#!/usr/bin/env bash
exec node "$ENTRY" --stdio "\$@"
WRAPPER
chmod +x /usr/local/bin/markdownlint-lsp-server

echo 'Markdown LSP installed.'