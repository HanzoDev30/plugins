#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

INSTALL_DIR=/usr/local/bin
VERSION=0.10.0

case "$(uname -m)" in
  aarch64|arm64) ASSET=taplo-linux-aarch64.gz ;;
  armv7l|armhf)   ASSET=taplo-linux-armv7.gz  ;;
  riscv64)        ASSET=taplo-linux-riscv64.gz ;;
  x86_64|amd64)   ASSET=taplo-linux-x86_64.gz ;;
  i386|i686)      ASSET=taplo-linux-x86.gz     ;;
  *) echo "Unsupported architecture: $(uname -m)"; exit 1 ;;
esac

URL="https://github.com/tamasfe/taplo/releases/download/${VERSION}/${ASSET}"

mkdir -p "$INSTALL_DIR"
TMP=$(mktemp /tmp/taplo-XXXXXX.gz)
trap 'rm -f "$TMP"' EXIT

echo ">> downloading $ASSET ($VERSION) ..."
curl -fsSL -o "$TMP" "$URL"

gunzip -c "$TMP" > "$INSTALL_DIR/taplo.new"
chmod +x "$INSTALL_DIR/taplo.new"
"$INSTALL_DIR/taplo.new" --version
mv "$INSTALL_DIR/taplo.new" "$INSTALL_DIR/taplo"

echo ">> taplo installed at $INSTALL_DIR/taplo"
echo "Lsp Done"
