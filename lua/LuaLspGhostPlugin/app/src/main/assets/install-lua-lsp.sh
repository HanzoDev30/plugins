#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

LUALS_VERSION="3.19.1"
INSTALL_DIR="/opt/lua-lsp"

case "$(uname -m)" in
  aarch64 | arm64) LUALS_ARCH="arm64" ;;
  x86_64 | amd64) LUALS_ARCH="x64" ;;
  armv7l | armv7) LUALS_ARCH="arm32" ;;
  *)
    echo "unsupported architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

URL="https://github.com/LuaLS/lua-language-server/releases/download/${LUALS_VERSION}/lua-language-server-${LUALS_VERSION}-linux-${LUALS_ARCH}.tar.gz"

if [ -x "${INSTALL_DIR}/bin/lua-language-server" ]; then
  echo "lua-language-server already installed in ${INSTALL_DIR}"
else
  command -v curl >/dev/null 2>&1 || {
    apt-get update -qq
    apt-get install -y -qq curl
  }
  command -v tar >/dev/null 2>&1 || apt-get install -y -qq tar
  mkdir -p "${INSTALL_DIR}"
  cd "${INSTALL_DIR}"
  curl -fsSL -o luals.tar.gz "${URL}"
  tar -xzf luals.tar.gz
  rm -f luals.tar.gz
  chmod +x "${INSTALL_DIR}/bin/lua-language-server" 2>/dev/null || true
fi

mkdir -p /usr/local/bin
ln -sf "${INSTALL_DIR}/bin/lua-language-server" /usr/local/bin/lua-language-server
"${INSTALL_DIR}/bin/lua-language-server" --version || true
