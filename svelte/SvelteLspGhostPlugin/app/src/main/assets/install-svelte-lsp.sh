#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

DIR=/opt/svelte-lsp

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi
NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "svelte: node runtime not found" >&2
  exit 1
fi
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 18 ]; then
  echo "svelte: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "svelte: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

mkdir -p "$DIR"

# resolve_bin <package dir> <bin name>: the executable file declared in the package's own "bin" field
resolve_bin() {
  "$NODE" -p "const b=require('$1/package.json').bin; typeof b==='string'?b:(b['$2']||Object.values(b)[0])"
}

# 2. svelte-language-server (svelteserver). typescript is installed next to it as a fallback: a
#    project's own node_modules/typescript and node_modules/svelte always win.
npm install --prefix "$DIR" --force --no-audit --no-fund svelte-language-server typescript@5

PKG="$DIR/node_modules/svelte-language-server"
ENTRY="$PKG/$(resolve_bin "$PKG" svelteserver)"
if [ ! -f "$ENTRY" ]; then
  echo "svelte: entry $ENTRY not found" >&2
  exit 1
fi
if [ ! -f "$DIR/node_modules/typescript/lib/typescript.js" ]; then
  echo "svelte: typescript.js missing in $DIR" >&2
  exit 1
fi

# 3. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 4. Wrapper with an absolute node path (the editor's environment may have no PATH).
mkdir -p /usr/local/bin
cat > /usr/local/bin/ghost-svelte-ls <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$ENTRY" "\$@"
WRAPPER
chmod 755 /usr/local/bin/ghost-svelte-ls

echo "Svelte language server installed (node $("$NODE" -v)): $ENTRY"
