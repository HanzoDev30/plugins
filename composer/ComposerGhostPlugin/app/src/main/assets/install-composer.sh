#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. Check if already installed
if command -v composer &>/dev/null; then
  echo "composer already installed - skipping."
  SKIP=1
else
  SKIP=0
fi

# 1. PHP CLI plus the extensions Composer and most packages need
if ! command -v php &>/dev/null; then
  apt update
  apt install -y php-cli php-xml php-mbstring php-curl php-zip unzip git
fi

# 2. Composer itself
if [ "$SKIP" -eq 0 ]; then
  if ! apt install -y composer; then
    php -r "copy('https://getcomposer.org/installer','composer-setup.php');"
    php composer-setup.php --install-dir=/usr/local/bin --filename=composer
    rm -f composer-setup.php
  fi
fi

# 3. Make sure it is on PATH for a login shell too
if [ ! -e /usr/local/bin/composer ]; then
  ln -sf "$(command -v composer)" /usr/local/bin/composer 2>/dev/null || true
fi
chmod +x /usr/local/bin/composer 2>/dev/null || true

composer --version || true
echo 'Composer installed.'
