#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

DIR=/opt/tailwindcss-lsp

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi
NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "tailwind-language-server: node runtime not found" >&2
  exit 1
fi
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 18 ]; then
  echo "tailwind-language-server: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "tailwind-language-server: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

# 2. The official Tailwind server does the real work; this plugin wraps it in a small
#    proxy (server.js, written next to this script). The proxy answers the
#    workspace/configuration requests the editor does not implement and merges the answers
#    of the html and emmet servers so normal HTML support is kept.
#      @tailwindcss/language-server  -> Tailwind utilities, hover (generated CSS), completion
#      vscode-html-language-server   -> proxied for normal HTML features
#      emmet-language-server         -> Emmet abbreviations
#    The IDE already installs the html and emmet servers system-wide: reuse those.
find_bin() {
  for d in /usr/local/bin /usr/bin; do
    if [ -x "$d/$1" ]; then echo "$d/$1"; return 0; fi
  done
  return 1
}
HTML_ENTRY="$(find_bin vscode-html-language-server || true)"
EMMET_ENTRY="$(find_bin emmet-language-server || true)"

mkdir -p "$DIR"
npm install -g --force --no-audit --no-fund @tailwindcss/language-server

TAILWIND_ENTRY=""
for candidate in /usr/bin/tailwindcss-language-server /usr/local/bin/tailwindcss-language-server; do
  if [ -x "$candidate" ]; then TAILWIND_ENTRY="$candidate"; break; fi
done
if [ -z "$TAILWIND_ENTRY" ]; then
  TAILWIND_ENTRY="$(command -v tailwindcss-language-server || true)"
fi
if [ -z "$TAILWIND_ENTRY" ] || [ ! -x "$TAILWIND_ENTRY" ]; then
  echo "tailwind-language-server: tailwindcss-language-server not found after install" >&2
  exit 1
fi

if [ -z "$HTML_ENTRY" ]; then
  npm install --prefix "$DIR" --force --no-audit --no-fund vscode-langservers-extracted
  PKG="$DIR/node_modules/vscode-langservers-extracted"
  HTML_REL="$("$NODE" -p "const b=require('$PKG/package.json').bin; typeof b==='string'?b:b['vscode-html-language-server']")"
  HTML_ENTRY="$PKG/$HTML_REL"
fi

if [ -z "$EMMET_ENTRY" ]; then
  # Emmet is optional: a failure here must not break Tailwind + HTML.
  if npm install --prefix "$DIR" --force --no-audit --no-fund @olrtg/emmet-language-server; then
    EPKG="$DIR/node_modules/@olrtg/emmet-language-server"
    EMMET_REL="$("$NODE" -p "const b=require('$EPKG/package.json').bin; typeof b==='string'?b:(b['emmet-language-server']||Object.values(b)[0])" || true)"
    if [ -n "$EMMET_REL" ] && [ -f "$EPKG/$EMMET_REL" ]; then EMMET_ENTRY="$EPKG/$EMMET_REL"; fi
  fi
  if [ -z "$EMMET_ENTRY" ]; then
    echo "tailwind-language-server: WARNING emmet server not available, continuing without Emmet" >&2
  fi
fi

if [ ! -f "$DIR/server.js" ]; then
  echo "tailwind-language-server: $DIR/server.js missing" >&2
  exit 1
fi

# 3. server.js reads the entries from here, so any launcher (wrapper or plain node) works.
cat > "$DIR/config.json" <<CONFIG
{ "tailwindServer": "$TAILWIND_ENTRY", "htmlServer": "$HTML_ENTRY", "emmetServer": "${EMMET_ENTRY}" }
CONFIG

# 4. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 5. Wrapper with an absolute node path (the editor's environment may have no PATH).
mkdir -p /usr/local/bin
cat > /usr/local/bin/tailwindcss-language-server-wrapper <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$DIR/server.js" "\$@"
WRAPPER
chmod 755 /usr/local/bin/tailwindcss-language-server-wrapper

echo "Tailwind LSP installed (node $("$NODE" -v))"
echo "  tailwind server: $TAILWIND_ENTRY"
echo "  html server    : $HTML_ENTRY"
echo "  emmet server   : ${EMMET_ENTRY:-<none>}"
