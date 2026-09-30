#!/usr/bin/env bash
# install-kmp-lsp.sh - installs the kmp-lsp language server into the Ghost IDE proot rootfs.
#
# kmp-lsp is a Rust/tree-sitter language server for Kotlin, Java and Swift. It needs no JDK,
# no Gradle import and no classpath resolution, so it works in a fresh workspace immediately.
#
# The release tarball ships both binaries, and they MUST stay in the same directory because
# kmp-lsp locates its sidecar next to its own executable:
#   /opt/kmp-lsp/kmp-lsp            the language server (stdio, default transport)
#   /opt/kmp-lsp/kmp-jar-indexer    native sidecar indexing dependency jars
# /usr/local/bin/kmp-lsp is only a thin forwarding wrapper, so the version can change
# without the plugin ever having to know where the server really lives.
#
# Both prebuilt binaries are built for glibc <= 2.28, so they run on bookworm as-is.
#
# ripgrep and fd are installed for the cross-file fallback (go-to-definition, references, file
# discovery). Without them the server still starts, but far less completely, so they are
# best-effort: a failed apt here is a warning, never a reason to abort the install.

set -uo pipefail

KMP_LSP_VERSION="${KMP_LSP_VERSION:-v0.27.0}"
REPO="Hessesian/kmp-lsp"
INSTALL_DIR="${KMP_LSP_INSTALL_DIR:-/opt/kmp-lsp}"
BIN_DIR="${KMP_LSP_BIN_DIR:-/usr/local/bin}"
WRAPPER="$BIN_DIR/kmp-lsp"

say() { printf ':: %s\n' "$*"; }
ok()  { printf '  ok %s\n' "$*"; }
warn() { printf '  !! %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

TMP=""
cleanup() { [ -n "$TMP" ] && rm -rf "$TMP"; return 0; }
trap cleanup EXIT

# ── 1. platform ──────────────────────────────────────────────────────────────
ARCH=""
case "$(uname -m)" in
  x86_64|amd64)  ARCH="x86_64" ;;
  aarch64|arm64) ARCH="aarch64" ;;
  *) die "unsupported architecture: $(uname -m) (kmp-lsp ships linux-x86_64 and linux-aarch64 only)" ;;
esac
PLATFORM="linux-${ARCH}"
say "platform: ${PLATFORM} (kmp-lsp ${KMP_LSP_VERSION})"

# ── 2. already installed? ────────────────────────────────────────────────────
if [ -x "$INSTALL_DIR/kmp-lsp" ] && [ -f "$INSTALL_DIR/VERSION" ] \
   && [ "$(cat "$INSTALL_DIR/VERSION" 2>/dev/null)" = "$KMP_LSP_VERSION" ]; then
  ok "kmp-lsp ${KMP_LSP_VERSION} already installed - refreshing launcher only"
  INSTALLED=1
else
  INSTALLED=0
fi

# ── 3. download + install the binaries ───────────────────────────────────────
# One tarball: the release publishes kmp-lsp and kmp-jar-indexer inside it, so there is a
# single file to fetch, checksum and unpack, and no gzip stream to reconstruct by hand.
GH_BASES=(
  "https://github.com"
  "https://ghproxy.net/https://github.com"
  "https://ghfast.top/https://github.com"
)

download() {
  local path="$1" dest="$2" base
  for base in "${GH_BASES[@]}"; do
    if curl -fL --retry 2 --connect-timeout 15 -o "$dest" \
        "${base}/${REPO}/releases/download/${KMP_LSP_VERSION}/${path}" 2>/dev/null && [ -s "$dest" ]; then
      return 0
    fi
    rm -f "$dest"
  done
  return 1
}

install_binaries() {
  local tarball="kmp-lsp-${PLATFORM}.tar.gz"
  TMP="$(mktemp -d)"

  say "downloading ${tarball}"
  download "$tarball" "$TMP/$tarball" \
    || die "could not download ${tarball} - no mirror reachable, check the network and retry"

  # Best effort: a mirror that serves the binaries but blocks sha256sums.txt must not fail
  # the install, and a checksum that cannot be parsed is not a reason to reject a download.
  if download "sha256sums.txt" "$TMP/sha256sums.txt" 2>/dev/null && [ -s "$TMP/sha256sums.txt" ]; then
    local expected actual
    expected="$(awk -v n="$tarball" '$2 == n || $2 == "*" n {print $1}' "$TMP/sha256sums.txt" | head -n1)"
    if [ -n "$expected" ]; then
      actual="$(sha256sum "$TMP/$tarball" | awk '{print $1}')"
      [ "$actual" = "$expected" ] && ok "sha256 verified" || die "sha256 mismatch for ${tarball}"
    fi
  else
    warn "sha256sums.txt unavailable - skipping checksum verification"
  fi

  command -v tar >/dev/null 2>&1 || apt-get install -y tar >/dev/null 2>&1 || true
  mkdir -p "$INSTALL_DIR"
  tar -xzf "$TMP/$tarball" -C "$INSTALL_DIR" \
    || die "could not unpack ${tarball}"

  [ -x "$INSTALL_DIR/kmp-lsp" ] || chmod 0755 "$INSTALL_DIR/kmp-lsp" 2>/dev/null || true
  [ -s "$INSTALL_DIR/kmp-lsp" ] || die "kmp-lsp missing after unpacking ${tarball}"
  [ -s "$INSTALL_DIR/kmp-jar-indexer" ] || warn "kmp-jar-indexer missing - library symbols stay limited to the rg fallback"

  chmod 0755 "$INSTALL_DIR/kmp-lsp" "$INSTALL_DIR/kmp-jar-indexer" 2>/dev/null || true
  printf '%s\n' "$KMP_LSP_VERSION" > "$INSTALL_DIR/VERSION"

  # Only when binutils happens to be there already - never worth installing it for this.
  if command -v strip >/dev/null 2>&1; then
    strip "$INSTALL_DIR/kmp-lsp" "$INSTALL_DIR/kmp-jar-indexer" 2>/dev/null || true
  fi

  ok "kmp-lsp ${KMP_LSP_VERSION} -> ${INSTALL_DIR}"
}

[ "$INSTALLED" -eq 0 ] && install_binaries

# ── 4. search tools for the cross-file fallback ───────────────────────────────
# Deliberately last and deliberately non-fatal: these degrade the server, they do not stop it.
install_search_tools() {
  local missing=()
  command -v rg >/dev/null 2>&1 || missing+=(ripgrep)
  command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 || missing+=(fd-find)

  if [ ${#missing[@]} -gt 0 ]; then
    say "installing search tools: ${missing[*]}"
    DEBIAN_FRONTEND=noninteractive apt-get install -y "${missing[@]}" >/dev/null 2>&1 \
      || warn "could not install ${missing[*]} - cross-file search will be degraded"
  fi

  # Debian ships the binary as fdfind; kmp-lsp spawns plain "fd".
  if ! command -v fd >/dev/null 2>&1 && command -v fdfind >/dev/null 2>&1; then
    ln -sf "$(command -v fdfind)" "$BIN_DIR/fd" 2>/dev/null || true
  fi

  command -v rg >/dev/null 2>&1 || warn "ripgrep missing - go-to-definition and references use the slower fallback"
  command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 \
    || warn "fd missing - workspace file discovery falls back to a directory walk"
}

install_search_tools

# ── 5. launcher ──────────────────────────────────────────────────────────────
# The plugin launches /usr/local/bin/kmp-lsp, so the wrapper owns the stable path and only the
# parts the host cannot supply itself; the real binaries stay in /opt/kmp-lsp together.
write_wrapper() {
  mkdir -p "$BIN_DIR"
  cat > "$WRAPPER" <<KMP_WRAPPER_EOF
#!/usr/bin/env bash
# kmp-lsp launcher - written by the KMP LSP plugin for Ghost IDE.
# Forwards to the real server in ${INSTALL_DIR}, which must hold both binaries: kmp-lsp finds
# its jar-indexer sidecar next to its own executable.
REAL="${INSTALL_DIR}/kmp-lsp"
if [ ! -x "\$REAL" ]; then
  echo "kmp-lsp: not installed at \$REAL - run 'Install KMP LSP' from the plugin manager." >&2
  exit 127
fi

# The host runs this as a non-interactive process, so no shell startup file is read. The host
# does set the working directory to the project root, and kmp-lsp resolves its root from
# KMP_LSP_WORKSPACE_ROOT before falling back to the client rootUri - saying it here makes the
# root the directory the file was opened from instead of a guess.
#
# Only with no arguments: with arguments this is a CLI subcommand (kmp-lsp sources --root .),
# which must not be second-guessed.
if [ "\$#" -eq 0 ] && [ -z "\${KMP_LSP_WORKSPACE_ROOT:-}" ]; then
  export KMP_LSP_WORKSPACE_ROOT="\$PWD"
fi

# User overrides, kept across re-installs. Edit this file freely; re-running the action
# leaves it alone.
[ -f "${INSTALL_DIR}/env" ] && . "${INSTALL_DIR}/env"

exec "\$REAL" "\$@"
KMP_WRAPPER_EOF
  chmod 0755 "$WRAPPER"
  ok "launcher -> ${WRAPPER}"
}

write_wrapper

# ── 6. environment overrides, written once and never overwritten ──────────────
# kmp-lsp finds library sources through ANDROID_HOME (android.jar) and GRADLE_USER_HOME
# (*-sources.jar), and the host's process launcher cannot pass environment variables - so this
# file is the only place they can be set. Everything above works without them; they only widen
# what resolves.
if [ ! -f "$INSTALL_DIR/env" ]; then
  cat > "$INSTALL_DIR/env" <<KMP_ENV_EOF
# Environment for kmp-lsp, sourced by ${WRAPPER} on every launch.
# The host cannot pass environment variables to the server, so anything kmp-lsp reads from the
# environment has to be exported here. Nothing below is required: the server starts and indexes
# the project's own files without any of it.
#
# Android SDK - gives android.jar, so Activity/Context/Compose resolve in a .java file.
# kmp-lsp looks at local.properties' sdk.dir first, then ANDROID_HOME, then ANDROID_SDK_ROOT.
#export ANDROID_HOME=/root/Android/sdk
#
# Gradle cache - gives *-sources.jar, which is what makes library hover docs and
# go-to-definition into library code work. Populate it by running one build first.
#export GRADLE_USER_HOME=/root/.gradle
#
# Workspace root - the launcher already sets this to the directory the file was opened from.
# Set it only to pin one project for every file.
#export KMP_LSP_WORKSPACE_ROOT=/root/projects/MyApp
#
# Server log - set to a path to get DEBUG logging into that file instead of stderr, which
# proot swallows. Without it a failing server is silent.
#export KMP_LSP_LOG_FILE=/tmp/kmp-lsp.log
KMP_ENV_EOF
  chmod 0644 "$INSTALL_DIR/env"
  ok "env overrides -> ${INSTALL_DIR}/env"
fi

# ── 7. smoke test ────────────────────────────────────────────────────────────
# The server speaks JSON-RPC over stdio; closing stdin must make it exit. A non-zero status
# here is reported but not fatal: proot can be slow to start a 19 MB binary, and the plugin
# stays registered either way.
if printf '' | timeout 60 "$INSTALL_DIR/kmp-lsp" >/dev/null 2>&1; then
  ok "kmp-lsp starts and shuts down cleanly"
else
  warn "kmp-lsp smoke test exited with status $? - if nothing works, run: ${BIN_DIR}/kmp-lsp-debug on"
fi

# Convenience toggle for the log file above, so the user never has to remember the path.
cat > "$BIN_DIR/kmp-lsp-debug" <<KMP_DEBUG_EOF
#!/usr/bin/env bash
# Toggles kmp-lsp debug logging (${KMP_LSP_VERSION}).
case "\${1:-}" in
  on)  touch /tmp/kmp-lsp-debug; echo "kmp-lsp debug logging ON  -> /tmp/kmp-lsp.log" ;;
  off) rm -f /tmp/kmp-lsp-debug; echo "kmp-lsp debug logging OFF" ;;
  *)   if [ -e /tmp/kmp-lsp-debug ]; then echo "ON  -> /tmp/kmp-lsp.log"; else echo "off"; fi ;;
esac
KMP_DEBUG_EOF
chmod 0755 "$BIN_DIR/kmp-lsp-debug"

cat <<KMP_SUMMARY_EOF

kmp-lsp ${KMP_LSP_VERSION} is ready.

  .kt .kts .java .swift  ->  ${WRAPPER}
  server log (debug)     ->  /tmp/kmp-lsp.log   (enable: kmp-lsp-debug on)
  real binaries          ->  ${INSTALL_DIR}
  environment overrides  ->  ${INSTALL_DIR}/env

Nothing above is required for the server to start. The Android SDK and the Gradle cache in
${INSTALL_DIR}/env only widen what resolves: without them project code still gets completion,
hover and go-to-definition, library and AndroidX symbols do not.
KMP_SUMMARY_EOF
