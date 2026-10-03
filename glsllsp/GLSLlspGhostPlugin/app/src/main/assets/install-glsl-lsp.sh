#!/usr/bin/env bash
# install-glsl-lsp.sh - installs the glsl_analyzer language server into the Ghost IDE proot rootfs.
#
# glsl_analyzer is a language server for GLSL. It is a single static musl binary with no runtime
# dependencies: no JDK, no glslangValidator, no toolchain, no project import, so it works in a
# fresh workspace immediately and costs about 6 MB on disk.
#
# The release zip contains one file:
#   /opt/glsl-lsp/glsl_analyzer    the language server (JSON-RPC over stdio by default)
# /usr/local/bin/glsl_analyzer is only a thin forwarding wrapper, so the version can change
# without the plugin ever having to know where the server really lives.
#
# The binary is statically linked against musl, so it runs on bookworm regardless of its glibc -
# that is why the release names say musl while the rootfs is glibc.

set -uo pipefail

GLSL_LSP_VERSION="${GLSL_LSP_VERSION:-v1.7.1}"
REPO="nolanderc/glsl_analyzer"
INSTALL_DIR="${GLSL_LSP_INSTALL_DIR:-/opt/glsl-lsp}"
BIN_DIR="${GLSL_LSP_BIN_DIR:-/usr/local/bin}"
WRAPPER="$BIN_DIR/glsl_analyzer"
SERVER="$INSTALL_DIR/glsl_analyzer"

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
  *) die "unsupported architecture: $(uname -m) (glsl_analyzer ships linux x86_64 and aarch64 only)" ;;
esac
PLATFORM="${ARCH}-linux-musl"
say "platform: ${PLATFORM} (glsl_analyzer ${GLSL_LSP_VERSION})"

# ── 2. already installed? ────────────────────────────────────────────────────
if [ -x "$SERVER" ] && [ -f "$INSTALL_DIR/VERSION" ] \
   && [ "$(cat "$INSTALL_DIR/VERSION" 2>/dev/null)" = "$GLSL_LSP_VERSION" ]; then
  ok "glsl_analyzer ${GLSL_LSP_VERSION} already installed - refreshing launcher only"
  INSTALLED=1
else
  INSTALLED=0
fi

# ── 3. download + install ────────────────────────────────────────────────────
GH_BASES=(
  "https://github.com"
  "https://ghproxy.net/https://github.com"
  "https://ghfast.top/https://github.com"
)

download() {
  local path="$1" dest="$2" base
  for base in "${GH_BASES[@]}"; do
    if curl -fL --retry 2 --connect-timeout 15 -o "$dest" \
        "${base}/${REPO}/releases/download/${GLSL_LSP_VERSION}/${path}" 2>/dev/null && [ -s "$dest" ]; then
      return 0
    fi
    rm -f "$dest"
  done
  return 1
}

# unzip if there is one, python3 otherwise: the release is a zip, and asking for an unzip that the
# rootfs does not have is a worse failure mode than extracting with an interpreter that is there.
unzip_archive() {
  local zip="$1" dest="$2"
  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "$zip" -d "$dest" && return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import sys,zipfile;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$zip" "$dest" \
      && return 0
  fi
  DEBIAN_FRONTEND=noninteractive apt-get install -y unzip >/dev/null 2>&1 \
    && command -v unzip >/dev/null 2>&1 \
    && unzip -oq "$zip" -d "$dest"
}

install_server() {
  # The release asset is named after the platform only - <arch>-linux-musl.zip, no project prefix.
  local zip="${PLATFORM}.zip"
  TMP="$(mktemp -d)"

  say "downloading ${zip}"
  download "$zip" "$TMP/$zip" \
    || die "could not download ${zip} - no mirror reachable, check the network and retry"

  command -v unzip >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1 \
    || DEBIAN_FRONTEND=noninteractive apt-get install -y unzip >/dev/null 2>&1 || true

  mkdir -p "$INSTALL_DIR"
  unzip_archive "$TMP/$zip" "$INSTALL_DIR" \
    || die "could not unpack ${zip} - no unzip and no python3 in the rootfs"

  [ -s "$INSTALL_DIR/bin/glsl_analyzer" ] && mv -f "$INSTALL_DIR/bin/glsl_analyzer" "$SERVER"
  [ -s "$SERVER" ] || die "glsl_analyzer missing after unpacking ${zip}"
  rmdir "$INSTALL_DIR/bin" 2>/dev/null || true
  chmod 0755 "$SERVER"

  printf '%s\n' "$GLSL_LSP_VERSION" > "$INSTALL_DIR/VERSION"

  # Only when binutils happens to be there already - never worth installing it for 6 MB of static
  # musl code that gains nothing from being stripped again.
  if command -v strip >/dev/null 2>&1; then
    strip "$SERVER" 2>/dev/null || true
  fi

  ok "glsl_analyzer ${GLSL_LSP_VERSION} -> ${SERVER}"
}

[ "$INSTALLED" -eq 0 ] && install_server

# ── 4. launcher ──────────────────────────────────────────────────────────────
# The plugin launches /usr/local/bin/glsl_analyzer, so the wrapper owns the stable path and only the
# parts the host cannot supply itself; the real binary stays in /opt/glsl-lsp.
write_wrapper() {
  mkdir -p "$BIN_DIR"
  cat > "$WRAPPER" <<GLSL_WRAPPER_EOF
#!/usr/bin/env bash
# glsl_analyzer launcher - written by the GLSL LSP plugin for Ghost IDE.
# Forwards to the real server in ${INSTALL_DIR}.
REAL="${SERVER}"
if [ ! -x "\$REAL" ]; then
  echo "glsl_analyzer: not installed at \$REAL - run 'Install GLSL LSP' from the plugin manager." >&2
  exit 127
fi

# User overrides, kept across re-installs. Edit this file freely; re-running the action
# leaves it alone.
[ -f "${INSTALL_DIR}/env" ] && . "${INSTALL_DIR}/env"

# The host runs this as a non-interactive process and does set the working directory to the project
# root, which is where the server resolves its workspace from when no rootUri arrives. Everything
# else is forwarded untouched: glsl_analyzer takes --stdio, --port or one of its CLI subcommands.
#
# Only when the debug flag is on, and only when the caller did not already pick a destination
# itself: --dev-mode redirects stderr, which is the only channel a language server may log on
# without corrupting the stdio framing.
for arg in "\$@"; do
  case "\$arg" in
    --dev-mode|--dev-mode=*) exec "\$REAL" "\$@" ;;
  esac
done
if [ -e /tmp/glsl-lsp-debug ]; then
  exec "\$REAL" "\$@" --dev-mode /tmp/glsl-analyzer.log
fi

exec "\$REAL" "\$@"
GLSL_WRAPPER_EOF
  chmod 0755 "$WRAPPER"
  ok "launcher -> ${WRAPPER}"
}

write_wrapper

# ── 5. shader compiler for the run button ────────────────────────────────────
# The language server analyses; it does not build. glslangValidator is what turns a shader into a
# SPIR-V module, which is the closest thing GLSL has to "running" - and it catches the linkage and
# type errors the analyzer does not model. Best effort by design: without it the run button falls
# back to the analyzer's own parse check, so a failed apt degrades the runner instead of breaking it.
install_compiler() {
  if command -v glslangValidator >/dev/null 2>&1; then
    ok "glslangValidator already present - SPIR-V builds enabled"
    return 0
  fi
  say "installing glslang-tools (shader compiler for the run button)"
  if DEBIAN_FRONTEND=noninteractive apt-get install -y glslang-tools >/dev/null 2>&1 \
     && command -v glslangValidator >/dev/null 2>&1; then
    ok "glslangValidator installed - run builds real SPIR-V"
  else
    warn "could not install glslang-tools - the run button falls back to the analyzer's parse check"
  fi
}

install_compiler

# ── 6. environment overrides, written once and never overwritten ──────────────
# The host's process launcher cannot pass environment variables, so this file is the only place
# they can be set. Nothing below is required: the server completes and hovers on builtins without
# any of it. It is for pinning the workspace root across re-installs and for keeping a log that
# survives proot swallowing stderr.
if [ ! -f "$INSTALL_DIR/env" ]; then
  cat > "$INSTALL_DIR/env" <<GLSL_ENV_EOF
# Environment for glsl_analyzer, sourced by ${WRAPPER} on every launch.
# The host cannot pass environment variables to the server, so anything read from the environment
# has to be exported here. Nothing below is required.
#
# Workspace root - the host already sets the working directory to the project root, which is what
# the server uses to find included files. Set this only to pin one project for every shader file.
#export GLSL_ANALYZER_WORKSPACE_ROOT=/root/projects/MyShader
#
# Nothing else is needed for logging: "glsl-lsp-debug on" makes the launcher pass --dev-mode, which
# sends the server's stderr to /tmp/glsl-analyzer.log instead of letting proot swallow it.
GLSL_ENV_EOF
  chmod 0644 "$INSTALL_DIR/env"
  ok "env overrides -> ${INSTALL_DIR}/env"
fi

# ── 7. smoke test ────────────────────────────────────────────────────────────
# A real parse rather than a bare --help: that is what proves the loader, the dynamic linker
# situation on this kernel and the binary itself are all fine. Non-zero is reported, not fatal -
# proot can be slow to start a freshly paged-in binary and the plugin stays registered either way.
PROBE="$(mktemp -d)/probe.frag"
printf '#version 330 core\nout vec4 fragColor;\nvoid main() { fragColor = vec4(1.0); }\n' > "$PROBE"
if "$SERVER" --parse-file "$PROBE" >/dev/null 2>&1; then
  ok "glsl_analyzer parses GLSL correctly"
else
  warn "glsl_analyzer smoke test failed - if nothing works, run: ${BIN_DIR}/glsl-lsp-debug on"
fi
rm -rf "$(dirname "$PROBE")"

cat > "$BIN_DIR/glsl-lsp-debug" <<GLSL_DEBUG_EOF
#!/usr/bin/env bash
# Toggles glsl_analyzer debug logging (${GLSL_LSP_VERSION}).
case "\${1:-}" in
  on)  touch /tmp/glsl-lsp-debug; echo "glsl_analyzer debug logging ON  -> /tmp/glsl-analyzer.log" ;;
  off) rm -f /tmp/glsl-lsp-debug; echo "glsl_analyzer debug logging OFF" ;;
  *)   if [ -e /tmp/glsl-lsp-debug ]; then echo "ON  -> /tmp/glsl-analyzer.log"; else echo "off"; fi ;;
esac
GLSL_DEBUG_EOF
chmod 0755 "$BIN_DIR/glsl-lsp-debug"

cat <<GLSL_SUMMARY_EOF

glsl_analyzer ${GLSL_LSP_VERSION} is ready.

  .vert .frag .geom .tesc .tese .comp .glsl .glsles  ->  ${WRAPPER}
  server log (debug)                               ->  /tmp/glsl-analyzer.log   (enable: glsl-lsp-debug on)
  real binary                                      ->  ${SERVER}
  environment overrides                            ->  ${INSTALL_DIR}/env
  run button (FAB)                                 ->  glslangValidator -V, then /root/glsl-output/*.spv

The shader stage is taken from the file extension, so a file named .frag is analysed as a fragment
shader. Rename a stray .glsl to its real stage (.vert / .frag / .comp) to get stage-correct
builtin completion.
GLSL_SUMMARY_EOF
