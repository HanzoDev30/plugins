#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    apt-get update
    apt-get install -y nodejs npm
fi

npm i -g @tailwindcss/language-server

REAL_BIN=""
for candidate in /usr/bin/tailwindcss-language-server /usr/local/bin/tailwindcss-language-server; do
    if [ -x "$candidate" ]; then
        REAL_BIN="$candidate"
        break
    fi
done

if [ -z "$REAL_BIN" ]; then
    REAL_BIN="$(command -v tailwindcss-language-server || true)"
fi

if [ -z "$REAL_BIN" ] || [ ! -x "$REAL_BIN" ]; then
    echo "ERROR: tailwindcss-language-server not found after install" >&2
    exit 1
fi

WRAPPER="/usr/local/bin/tailwindcss-language-server-wrapper"
mkdir -p /usr/local/bin
cat > "$WRAPPER" <<EOF
#!/bin/sh
exec "$REAL_BIN" --stdio "\$@"
EOF
chmod +x "$WRAPPER"

echo "Tailwind LSP wrapper installed: $WRAPPER -> $REAL_BIN"