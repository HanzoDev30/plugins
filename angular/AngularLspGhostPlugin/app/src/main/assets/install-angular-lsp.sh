#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "angular-language-server: node runtime not found" >&2
  exit 1
fi

# 2. Recent @angular/language-server needs Node >= 20. Debian's apt node can be older,
#    so upgrade through the "n" version manager when needed (best effort).
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 20 ]; then
  echo "angular-language-server: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "angular-language-server: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

# 3. npm i @angular/language-server (private prefix, does not touch global packages).
#    @angular/language-service + typescript@5 are the fallback the server probes when the
#    project has no node_modules of its own. A project's own copies always win.
DIR=/opt/angular-lsp
mkdir -p "$DIR"
npm install --prefix "$DIR" --force --no-audit --no-fund \
  @angular/language-server @angular/language-service typescript@5

# 4. Resolve the real server entry from the package's own bin field.
PKG="$DIR/node_modules/@angular/language-server"
if [ ! -f "$PKG/package.json" ]; then
  echo "angular-language-server: package not found in $PKG" >&2
  exit 1
fi
BIN_REL="$("$NODE" -p "const b=require('$PKG/package.json').bin; typeof b==='string'?b:(b.ngserver||Object.values(b)[0])")"
ENTRY="$PKG/$BIN_REL"
if [ ! -f "$ENTRY" ]; then
  echo "angular-language-server: entry $ENTRY not found" >&2
  exit 1
fi
if [ ! -f "$DIR/node_modules/typescript/lib/typescript.js" ]; then
  echo "angular-language-server: typescript.js missing in $DIR" >&2
  exit 1
fi

# 5. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 6. Wrapper with an absolute node path (the editor's environment may have no PATH).
#    The plugin appends --stdio and the probe locations.
mkdir -p /usr/local/bin
cat > /usr/local/bin/angular-language-server <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$ENTRY" "\$@"
WRAPPER
chmod 755 /usr/local/bin/angular-language-server

echo "Angular LSP installed: $ENTRY (node $("$NODE" -v))"
