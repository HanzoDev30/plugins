#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# 1. Install Go
apt update && apt install -y golang-go

# 2. Install gopls (compatible with Debian's Go 1.19)
export PATH="$(go env GOPATH)/bin:$PATH"
go install golang.org/x/tools/gopls@v0.14.2

# 3. Symlink gopls to /usr/local/bin
ln -sf "$(go env GOPATH)/bin/gopls" /usr/local/bin/gopls

# 4. Wrapper launcher
cat > /usr/local/bin/go-language-server <<'WRAPPER'
#!/usr/bin/env bash
GOPLS=$(command -v gopls 2>/dev/null || echo /usr/local/bin/gopls)
exec "$GOPLS" serve
WRAPPER
chmod +x /usr/local/bin/go-language-server

echo 'Go LSP installed.'