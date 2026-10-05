#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

DIR=/opt/docker-lsp

# 1. Node.js + npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi
NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "docker: node runtime not found" >&2
  exit 1
fi
MAJOR="$("$NODE" -p 'process.versions.node.split(".")[0]')"
if [ "$MAJOR" -lt 18 ]; then
  echo "docker: node $MAJOR is too old, installing a newer node with n ..."
  npm install -g n && n lts || echo "docker: could not upgrade node" >&2
  hash -r
  NODE="$(command -v node)"
fi

mkdir -p "$DIR"

# resolve_bin <package dir> <bin name>: the executable file declared in the package's own "bin" field
resolve_bin() {
  "$NODE" -p "const b=require('$1/package.json').bin; typeof b==='string'?b:(b['$2']||Object.values(b)[0])"
}

# 2. Two servers: Dockerfile (docker-langserver) and Docker Compose (docker-compose-langserver)
npm install --prefix "$DIR" --force --no-audit --no-fund dockerfile-language-server-nodejs @microsoft/compose-language-service

DF_PKG="$DIR/node_modules/dockerfile-language-server-nodejs"
CP_PKG="$DIR/node_modules/@microsoft/compose-language-service"
DF_ENTRY="$DF_PKG/$(resolve_bin "$DF_PKG" docker-langserver)"
CP_ENTRY="$CP_PKG/$(resolve_bin "$CP_PKG" docker-compose-langserver)"
for f in "$DF_ENTRY" "$CP_ENTRY" "$DIR/docker-shim.js"; do
  if [ ! -f "$f" ]; then
    echo "docker: $f not found" >&2
    exit 1
  fi
done

# 3. The editor process is not root: make the whole tree reachable for it.
chmod -R a+rX "$DIR"

# 4. Wrappers with an absolute node path (the editor's environment may have no PATH). The shim only
#    rewrites the languageId of opened documents (the compose service expects "dockercompose").
mkdir -p /usr/local/bin
cat > /usr/local/bin/ghost-dockerfile-ls <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$DIR/docker-shim.js" --server "$DF_ENTRY" --language-id dockerfile "\$@"
WRAPPER
chmod 755 /usr/local/bin/ghost-dockerfile-ls
cat > /usr/local/bin/ghost-compose-ls <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" "$DIR/docker-shim.js" --server "$CP_ENTRY" --language-id dockercompose "\$@"
WRAPPER
chmod 755 /usr/local/bin/ghost-compose-ls

echo "Docker language servers installed (node $("$NODE" -v))"
echo "  dockerfile: $DF_ENTRY"
echo "  compose   : $CP_ENTRY"
