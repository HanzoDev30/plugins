#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

DIR=/opt/bootstrap-lsp

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi
NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "bootstrap-language-server: node runtime not found" >&2
  exit 1
fi
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 18 ]; then
  echo "bootstrap-language-server: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "bootstrap-language-server: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

# 2. There is no official Bootstrap language server. The plugin ships its own proxy
#    (server.js, written next to this script). It needs:
#      bootstrap                     -> bootstrap.css, the source of the class names
#      vscode-html-language-server   -> proxied for normal HTML features
#      emmet-language-server         -> Emmet abbreviations, asked next to the html server
#    The IDE already installs the last two system-wide: reuse those, install only what is missing.
find_bin() {
  for d in /usr/local/bin /usr/bin; do
    if [ -x "$d/$1" ]; then echo "$d/$1"; return 0; fi
  done
  return 1
}
HTML_ENTRY="$(find_bin vscode-html-language-server || true)"
EMMET_ENTRY="$(find_bin emmet-language-server || true)"

mkdir -p "$DIR"
PKGS="bootstrap"
if [ -z "$HTML_ENTRY" ]; then PKGS="$PKGS vscode-langservers-extracted"; fi
npm install --prefix "$DIR" --force --no-audit --no-fund $PKGS

if [ -z "$EMMET_ENTRY" ]; then
  # Emmet is optional: a failure here must not break Bootstrap + HTML.
  if npm install --prefix "$DIR" --force --no-audit --no-fund @olrtg/emmet-language-server; then
    EPKG="$DIR/node_modules/@olrtg/emmet-language-server"
    EMMET_REL="$("$NODE" -p "const b=require('$EPKG/package.json').bin; typeof b==='string'?b:(b['emmet-language-server']||Object.values(b)[0])" || true)"
    if [ -n "$EMMET_REL" ] && [ -f "$EPKG/$EMMET_REL" ]; then EMMET_ENTRY="$EPKG/$EMMET_REL"; fi
  fi
  if [ -z "$EMMET_ENTRY" ]; then
    echo "bootstrap-language-server: WARNING emmet server not available, continuing without Emmet" >&2
  fi
fi

# 3. Resolve the html server entry when we had to install it ourselves.
if [ -z "$HTML_ENTRY" ]; then
  PKG="$DIR/node_modules/vscode-langservers-extracted"
  HTML_REL="$("$NODE" -p "const b=require('$PKG/package.json').bin; typeof b==='string'?b:b['vscode-html-language-server']")"
  HTML_ENTRY="$PKG/$HTML_REL"
fi
if [ ! -f "$HTML_ENTRY" ]; then
  echo "bootstrap-language-server: html server entry $HTML_ENTRY not found" >&2
  exit 1
fi
if [ ! -f "$DIR/node_modules/bootstrap/dist/css/bootstrap.css" ]; then
  echo "bootstrap-language-server: bootstrap.css missing in $DIR" >&2
  exit 1
fi
if [ ! -f "$DIR/server.js" ]; then
  echo "bootstrap-language-server: $DIR/server.js missing" >&2
  exit 1
fi

# 4. server.js reads the entries from here, so any launcher (wrapper or plain node) works.
cat > "$DIR/config.json" <<CONFIG
{ "htmlServer": "$HTML_ENTRY", "emmetServer": "$EMMET_ENTRY" }
CONFIG

# 5. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 6. Wrapper with an absolute node path (the editor's environment may have no PATH).
mkdir -p /usr/local/bin
cat > /usr/local/bin/bootstrap-language-server <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$DIR/server.js" "\$@"
WRAPPER
chmod 755 /usr/local/bin/bootstrap-language-server

echo "Bootstrap LSP installed (node $("$NODE" -v))"
echo "  html server : $HTML_ENTRY"
echo "  emmet server: ${EMMET_ENTRY:-<none>}"
