#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Install Node.js and npm (only if missing)
if ! command -v npm &>/dev/null; then
  apt update && apt install -y nodejs npm
fi

# 2. Install the Vue language server globally.
#    npm registers the `vue-language-server` bin itself; no wrapper needed.
npm install -g --force @vue/language-server

if ! command -v vue-language-server &>/dev/null; then
  echo 'vue-language-server: not found after npm install' >&2
  npm root -g >&2 || true
  exit 1
fi

echo 'Vue LSP installed.'