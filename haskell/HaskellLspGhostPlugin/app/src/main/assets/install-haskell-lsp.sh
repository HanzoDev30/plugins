#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 0. Base build deps (gcc/make are needed for HLS on some platforms) + curl
if ! command -v curl &>/dev/null || ! command -v gcc &>/dev/null; then
  apt update && apt install -y curl gcc make build-essential
fi

# 1. Install GHCup (Haskell toolchain manager) - only if missing
if ! command -v ghcup &>/dev/null && [ ! -x "$HOME/.ghcup/bin/ghcup" ]; then
  export BOOTSTRAP_HASKELL_NONINTERACTIVE=1
  export BOOTSTRAP_HASKELL_INSTALL_NO_STACK=1
  curl --proto '=https' --tlsv1.2 -sSf https://get-ghcup.haskell.org | sh
fi
export PATH="$HOME/.ghcup/bin:$PATH"

# 2. Install haskell-language-server (HLS)
if ! command -v haskell-language-server &>/dev/null; then
  ghcup install hls
fi
export PATH="$HOME/.ghcup/bin:$PATH"

# 3. Wrapper launcher
cat > /usr/local/bin/haskell-language-server <<'WRAPPER'
#!/usr/bin/env bash
export PATH="$HOME/.ghcup/bin:$PATH"
HLS=$(command -v haskell-language-server 2>/dev/null || true)
if [ -z "$HLS" ]; then
  for c in "$HOME/.ghcup/bin/haskell-language-server" /usr/local/ghcup/bin/haskell-language-server /usr/bin/haskell-language-server /usr/local/bin/haskell-language-server; do
    [ -e "$c" ] && HLS="$c" && break
  done
fi
if [ -z "$HLS" ]; then
  echo 'haskell-language-server: not found' >&2
  exit 1
fi
exec "$HLS" --lsp "$@"
WRAPPER
chmod +x /usr/local/bin/haskell-language-server

echo 'Haskell LSP installed.'