#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# The editor runs the language server as its own uid, not as root. This image
# has umask 0077, so everything installed here has to be made world readable.
umask 022

# 1. Install Node.js and npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

NODE="$(command -v node)"
if [ ! -x "$NODE" ]; then
  echo "astro-language-server: node runtime not found" >&2
  exit 1
fi

# 2. Install the Astro language server globally.
npm install -g --force @astrojs/language-server

# 3. Resolve the real server entry. Never write inside the npm package dir.
ENTRY="$(npm root -g)/@astrojs/language-server/bin/nodeServer.js"
if [ ! -f "$ENTRY" ]; then
  for c in /usr/lib/node_modules/@astrojs/language-server/bin/nodeServer.js \
           /usr/local/lib/node_modules/@astrojs/language-server/bin/nodeServer.js; do
    [ -f "$c" ] && ENTRY="$c" && break
  done
fi
if [ ! -f "$ENTRY" ]; then
  echo 'astro-language-server: entry not found after npm install' >&2
  npm root -g >&2 || true
  exit 1
fi

# 4. Volar refuses to initialize without a TypeScript 5 JavaScript library.
#    typescript@7 ships no typescript.js, so keep a private copy for the Astro
#    server instead of replacing the global typescript other tools rely on.
TSDK=/opt/astro-lsp/node_modules/typescript/lib
if [ ! -f "$TSDK/typescript.js" ]; then
  npm install --prefix /opt/astro-lsp --force --no-audit --no-fund typescript@5
fi
if [ ! -f "$TSDK/typescript.js" ]; then
  echo "astro-language-server: typescript.js not found in $TSDK" >&2
  exit 1
fi

# 6. The editor process is not root: make the whole tree reachable for it.
for d in /usr/lib/node_modules/@astrojs /usr/local/lib/node_modules/@astrojs /opt/astro-lsp; do
  [ -d "$d" ] && chmod -R a+rX "$d"
done
touch /opt/astro-lsp/lsp-traffic.log
chmod 666 /opt/astro-lsp/lsp-traffic.log

echo 'Astro LSP installed.'

# 5. Stdio transport is required. The proxy guarantees it, guarantees Volar gets
#    a tsdk even when the editor forwards no initializationOptions, and records
#    the handshake in /opt/astro-lsp/lsp-traffic.log for troubleshooting.
cat > /usr/local/lib/astro-language-server.js <<PROXY
'use strict';

const { spawn } = require('child_process');
const fs = require('fs');

const ENTRY = '$ENTRY';
const TSDK = '$TSDK';
const LOG = '/opt/astro-lsp/lsp-traffic.log';
const LOG_LIMIT = 262144;

function log(direction, text) {
  try {
    if (fs.existsSync(LOG) && fs.statSync(LOG).size > LOG_LIMIT) {
      fs.truncateSync(LOG, 0);
    }
    fs.appendFileSync(LOG, direction + ' ' + text.replace(/[\r\n]+/g, ' ') + '\n');
  } catch (e) {
  }
}

function encode(message) {
  const body = Buffer.from(JSON.stringify(message), 'utf8');
  return Buffer.concat([Buffer.from('Content-Length: ' + body.length + '\r\n\r\n'), body]);
}

function decode(buffer) {
  const split = buffer.indexOf('\r\n\r\n');
  if (split < 0) {
    return null;
  }
  const match = /content-length:\s*(\d+)/i.exec(buffer.subarray(0, split).toString('ascii'));
  if (!match) {
    return null;
  }
  const start = split + 4;
  const length = Number(match[1]);
  if (buffer.length < start + length) {
    return null;
  }
  let message = null;
  try {
    message = JSON.parse(buffer.subarray(start, start + length).toString('utf8'));
  } catch (e) {
    message = null;
  }
  return { message, rest: buffer.subarray(start + length) };
}

function withTsdk(message) {
  if (!message || message.method !== 'initialize') {
    return message;
  }
  const params = message.params || (message.params = {});
  const options = params.initializationOptions || (params.initializationOptions = {});
  const typescript = options.typescript || (options.typescript = {});
  if (typeof typescript.tsdk !== 'string' || !typescript.tsdk) {
    typescript.tsdk = TSDK;
  }
  return message;
}

log('--', 'launch ' + ENTRY + ' tsdk=' + TSDK);

const server = spawn(process.execPath, [ENTRY, '--stdio'], { stdio: ['pipe', 'pipe', 'inherit'] });

let pending = Buffer.alloc(0);
process.stdin.on('data', chunk => {
  pending = Buffer.concat([pending, chunk]);
  for (;;) {
    const frame = decode(pending);
    if (!frame) {
      return;
    }
    pending = frame.rest;
    log('>>', JSON.stringify(frame.message));
    server.stdin.write(encode(withTsdk(frame.message)));
  }
});

server.stdout.on('data', chunk => {
  log('<<', chunk.toString('utf8'));
  process.stdout.write(chunk);
});

server.on('exit', code => log('--', 'server exited with ' + code));

process.stdin.on('end', () => server.stdin.end());
process.on('SIGTERM', () => server.kill());
PROXY

# Wrapper: absolute node path, the editor's environment may have no PATH.
mkdir -p /usr/local/bin
cat > /usr/local/bin/astro-language-server <<WRAPPER
#!/usr/bin/env bash
exec "$NODE" /usr/local/lib/astro-language-server.js "\$@"
WRAPPER
chmod 755 /usr/local/bin/astro-language-server /usr/local/lib/astro-language-server.js

echo 'Astro LSP installed.'