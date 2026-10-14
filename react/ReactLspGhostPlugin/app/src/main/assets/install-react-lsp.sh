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
  echo "react-language-server: node runtime not found" >&2
  exit 1
fi

# 2. TypeScript 5 plus the language server itself. TypeScript is pinned to 5 because
#    the language server needs typescript/lib/tsserver.js, which newer majors drop.
npm install -g --force --no-audit --no-fund typescript@5 typescript-language-server

# 3. React type definitions in a private prefix, used by projects that do not ship
#    their own node_modules. A project's own copies always win.
DIR=/opt/react-lsp
mkdir -p "$DIR"
npm install --prefix "$DIR" --force --no-audit --no-fund \
  typescript@5 @types/react @types/react-dom

# 4. Resolve the real server entry from the package's own bin field.
PKG="$(npm root -g)/typescript-language-server"
if [ ! -f "$PKG/package.json" ]; then
  for c in /usr/lib/node_modules/typescript-language-server \
           /usr/local/lib/node_modules/typescript-language-server; do
    [ -f "$c/package.json" ] && PKG="$c" && break
  done
fi
if [ ! -f "$PKG/package.json" ]; then
  echo "react-language-server: typescript-language-server package not found" >&2
  npm root -g >&2 || true
  exit 1
fi
BIN_REL="$("$NODE" -p "const b=require('$PKG/package.json').bin; typeof b==='string'?b:(b['typescript-language-server']||Object.values(b)[0])")"
ENTRY="$PKG/$BIN_REL"
if [ ! -f "$ENTRY" ]; then
  echo "react-language-server: entry $ENTRY not found" >&2
  exit 1
fi

# 5. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$PKG" "$DIR" 2>/dev/null || true

# 6. Wrapper with an absolute node path (the editor's environment may have no PATH).
mkdir -p /usr/local/bin
cat > /usr/local/bin/react-language-server <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$ENTRY" "\$@"
WRAPPER
chmod 755 /usr/local/bin/react-language-server

echo "React language server installed: $ENTRY (node $("$NODE" -v))"
