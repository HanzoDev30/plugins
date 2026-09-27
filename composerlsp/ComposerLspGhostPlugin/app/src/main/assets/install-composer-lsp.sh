#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. Check what is already installed
if command -v composer &>/dev/null && command -v composer-json-language-server &>/dev/null; then
  echo "composer and the manifest language server already installed - skipping."
  exit 0
fi

# 1. PHP CLI plus the extensions Composer needs
if ! command -v php &>/dev/null; then
  apt update
  apt install -y php-cli php-xml php-mbstring php-curl php-zip unzip git
fi

# 2. Composer
if ! command -v composer &>/dev/null; then
  if ! apt install -y composer; then
    php -r "copy('https://getcomposer.org/installer','composer-setup.php');"
    php composer-setup.php --install-dir=/usr/local/bin --filename=composer
    rm -f composer-setup.php
  fi
fi
[ -e /usr/local/bin/composer ] || ln -sf "$(command -v composer)" /usr/local/bin/composer
chmod +x /usr/local/bin/composer 2>/dev/null || true

# 3. Node.js, only if missing (Ghost IDE ships it, but not always in PATH)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 4. Install / repair @t1ckbase/vscode-langservers-extracted globally.
#    Same package the JSON plugin uses. --force makes npm re-extract every file.
npm install -g --force @t1ckbase/vscode-langservers-extracted

# 5. Resolve the real JSON server entry. Never write inside the npm package dir.
ENTRY="$(npm root -g)/@t1ckbase/vscode-langservers-extracted/bin/json.js"
if [ ! -f "$ENTRY" ]; then
  for c in /usr/lib/node_modules/@t1ckbase/vscode-langservers-extracted/bin/json.js \
           /usr/local/lib/node_modules/@t1ckbase/vscode-langservers-extracted/bin/json.js; do
    [ -f "$c" ] && ENTRY="$c" && break
  done
fi
if [ ! -f "$ENTRY" ]; then
  echo 'composer-json-language-server: vscode-json-language-server entry not found' >&2
  exit 1
fi

# 6. Wrapper launcher (stdio transport is required)
cat > /usr/local/bin/composer-json-language-server <<WRAPPER
#!/usr/bin/env bash
exec node "$ENTRY" --stdio "\$@"
WRAPPER
chmod +x /usr/local/bin/composer-json-language-server

composer --version || true
echo 'Composer Lsp installed.'
