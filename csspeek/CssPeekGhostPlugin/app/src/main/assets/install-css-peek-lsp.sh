#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

DIR=/opt/csspeek-lsp

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi
NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "css-peek: node runtime not found" >&2
  exit 1
fi
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 18 ]; then
  echo "css-peek: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "css-peek: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

# 2. CSS Peek itself needs no npm package: server.js + css-peek-core.js are written next to this
#    script and use only Node's standard library. Behind it run the servers the IDE already
#    installs system-wide (html + emmet); only what is missing gets installed here.
find_bin() {
  for d in /usr/local/bin /usr/bin; do
    if [ -x "$d/$1" ]; then echo "$d/$1"; return 0; fi
  done
  return 1
}
HTML_ENTRY="$(find_bin vscode-html-language-server || true)"
EMMET_ENTRY="$(find_bin emmet-language-server || true)"

mkdir -p "$DIR"

if [ -z "$HTML_ENTRY" ]; then
  npm install --prefix "$DIR" --force --no-audit --no-fund vscode-langservers-extracted
  PKG="$DIR/node_modules/vscode-langservers-extracted"
  HTML_REL="$("$NODE" -p "const b=require('$PKG/package.json').bin; typeof b==='string'?b:b['vscode-html-language-server']")"
  HTML_ENTRY="$PKG/$HTML_REL"
fi
if [ ! -f "$HTML_ENTRY" ]; then
  echo "css-peek: html server entry $HTML_ENTRY not found" >&2
  exit 1
fi

# Optional extras: a failure here must not break CSS Peek.
if [ -z "$EMMET_ENTRY" ]; then
  if npm install --prefix "$DIR" --force --no-audit --no-fund @olrtg/emmet-language-server; then
    EPKG="$DIR/node_modules/@olrtg/emmet-language-server"
    EMMET_REL="$("$NODE" -p "const b=require('$EPKG/package.json').bin; typeof b==='string'?b:(b['emmet-language-server']||Object.values(b)[0])" || true)"
    if [ -n "$EMMET_REL" ] && [ -f "$EPKG/$EMMET_REL" ]; then EMMET_ENTRY="$EPKG/$EMMET_REL"; fi
  fi
  if [ -z "$EMMET_ENTRY" ]; then
    echo "css-peek: WARNING emmet server not available, continuing without Emmet" >&2
  fi
fi
# bootstrap.css: class completion for Bootstrap projects that load it from a CDN only
if [ ! -f /opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.css ]; then
  npm install --prefix "$DIR" --force --no-audit --no-fund bootstrap || echo "css-peek: WARNING bootstrap package not installed (Bootstrap completion only works with a project copy)" >&2
fi

for f in server.js css-peek-core.js; do
  if [ ! -f "$DIR/$f" ]; then
    echo "css-peek: $DIR/$f missing" >&2
    exit 1
  fi
done

# 3. server.js reads the entries from here, so any launcher (wrapper or plain node) works.
cat > "$DIR/config.json" <<CONFIG
{ "htmlServer": "$HTML_ENTRY", "emmetServer": "$EMMET_ENTRY" }
CONFIG

# 4. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 5. Wrapper with an absolute node path (the editor's environment may have no PATH).
mkdir -p /usr/local/bin
cat > /usr/local/bin/css-peek-language-server <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$DIR/server.js" "\$@"
WRAPPER
chmod 755 /usr/local/bin/css-peek-language-server

echo "CSS Peek installed (node $("$NODE" -v))"
echo "  html server : $HTML_ENTRY"
echo "  emmet server: ${EMMET_ENTRY:-<none>}"
