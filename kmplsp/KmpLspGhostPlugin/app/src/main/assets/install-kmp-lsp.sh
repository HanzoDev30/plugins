#!/usr/bin/env bash
# install-kmp-lsp.sh - installs the kmp-lsp language server into the Ghost IDE proot rootfs.
#
# kmp-lsp is a Rust/tree-sitter language server for Kotlin, Java and Swift. Unlike the
# JVM-based Kotlin language server it needs no JDK, no Gradle import and no build-system
# classpath resolution, so it is usable in a fresh workspace immediately.
#
# Two binaries are installed, and they MUST live in the same directory:
#   kmp-lsp            - the LSP server itself (stdio transport, default)
#   kmp-jar-indexer    - native sidecar that indexes dependency jars/sources jars
# kmp-lsp locates its sidecar next to its own executable (src/sidecar.rs), so both go
# into /opt/kmp-lsp and /usr/local/bin/kmp-lsp is only a thin forwarding wrapper.
#
# Requirements on the rootfs (Debian 12 / bookworm, glibc 2.36):
#   kmp-lsp          needs libc + libgcc_s.so.1          (max GLIBC_2.28)
#   kmp-jar-indexer  needs libc + libz.so.1              (max GLIBC_2.34)
# Both prebuilt release binaries therefore run on bookworm as-is.
#
# kmp-lsp also shells out to `rg` and `fd` for its cross-file fallback (go-to-definition,
# references, file discovery). Without them it still works, but far less completely, so
# ripgrep and fd-find are installed here - including the `fd` -> `fdfind` symlink that
# Debian's fd-find package needs.

set -euo pipefail

KMP_LSP_VERSION="${KMP_LSP_VERSION:-v0.27.0}"
REPO="Hessesian/kmp-lsp"
INSTALL_DIR="/opt/kmp-lsp"
BIN_DIR="/usr/local/bin"
WRAPPER="$BIN_DIR/kmp-lsp"
DEBUG_FLAG="/tmp/kmp-lsp-debug"
LOG_FILE="/tmp/kmp-lsp.log"

export DEBIAN_FRONTEND=noninteractive

# Scratch space for the downloads, removed on any exit path.
KMP_TMP=""
cleanup() { [ -n "$KMP_TMP" ] && rm -rf "$KMP_TMP"; }
trap cleanup EXIT

say()  { printf ':: %s\n' "$*"; }
ok()   { printf '\033[32m  ok\033[0m %s\n' "$*"; }
warn() { printf '\033[33m  !!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# ── 1. host dependencies ─────────────────────────────────────────────────────
has_lib() {
  local name="$1" d
  for d in /lib/*/ /usr/lib/*/ /lib/ /usr/lib/; do
    [ -e "${d}${name}" ] && return 0
  done
  return 1
}

install_deps() {
  local missing=()
  command -v curl >/dev/null 2>&1 || missing+=(curl)
  command -v tar >/dev/null 2>&1 || missing+=(tar)
  command -v gunzip >/dev/null 2>&1 || missing+=(gzip)
  has_lib libgcc_s.so.1 || missing+=(libgcc-s1)
  has_lib libz.so.1 || missing+=(zlib1g)
  command -v rg >/dev/null 2>&1 || missing+=(ripgrep)
  command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 || missing+=(fd-find)

  if [ ${#missing[@]} -gt 0 ]; then
    say "installing host packages: ${missing[*]}"
    apt-get update >/dev/null 2>&1 || warn "apt-get update failed; trying the install anyway"
    apt-get install -y "${missing[@]}" >/dev/null 2>&1 \
      || die "could not install: ${missing[*]} - run 'apt-get install -y ${missing[*]}' in the terminal and re-run this action"
  fi

  # Debian ships the fd binary as `fdfind`; kmp-lsp spawns plain `fd`.
  if ! command -v fd >/dev/null 2>&1 && command -v fdfind >/dev/null 2>&1; then
    ln -sf "$(command -v fdfind)" "$BIN_DIR/fd" 2>/dev/null || true
  fi
  command -v rg >/dev/null 2>&1 || warn "ripgrep missing - cross-file search will be degraded"
  command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 \
    || warn "fd missing - workspace file discovery falls back to a slower directory walk"
}

# ── 2. platform ──────────────────────────────────────────────────────────────
detect_platform() {
  local m
  m="$(uname -m)"
  case "$m" in
    x86_64|amd64)  ARCH="x86_64" ;;
    aarch64|arm64) ARCH="aarch64" ;;
    *) die "unsupported architecture: $m (kmp-lsp ships linux-x86_64 and linux-aarch64 only)" ;;
  esac
  PLATFORM="linux-${ARCH}"
  say "platform: ${PLATFORM} (kmp-lsp ${KMP_LSP_VERSION})"
}

# ── 3. download (direct GitHub, then mirrors) ────────────────────────────────
GH_BASES=(
  "https://github.com"
  "https://ghproxy.net/https://github.com"
  "https://ghfast.top/https://github.com"
  "https://gh-proxy.com/https://github.com"
)

# fetch <release-path> <destination>
fetch() {
  local path="$1" dest="$2" base
  for base in "${GH_BASES[@]}"; do
    if curl -fL --retry 2 --connect-timeout 15 -o "$dest" "${base}/${REPO}/releases/download/${KMP_LSP_VERSION}/${path}" 2>/dev/null; then
      [ -s "$dest" ] && return 0
    fi
    rm -f "$dest"
  done
  return 1
}

download_binaries() {
  local tmp lsp_tarball sidecar_gz
  lsp_tarball="kmp-lsp-${PLATFORM}.tar.gz"
  sidecar_gz="kmp-jar-indexer-${PLATFORM}.gz"

  tmp="$(mktemp -d)"
  KMP_TMP="$tmp"

  say "downloading ${lsp_tarball}"
  fetch "$lsp_tarball" "$tmp/$lsp_tarball" \
    || die "could not download ${lsp_tarball} - no mirror reachable; check the network and retry"

  say "downloading ${sidecar_gz}"
  fetch "$sidecar_gz" "$tmp/$sidecar_gz" \
    || die "could not download ${sidecar_gz} - no mirror reachable; check the network and retry"

  # Checksum verification is best-effort: the release publishes sha256sums.txt, but a
  # mirror that serves the binaries while blocking that file must not fail the install.
  if fetch "sha256sums.txt" "$tmp/sha256sums.txt" 2>/dev/null && [ -s "$tmp/sha256sums.txt" ]; then
    local name expected actual
    for name in "$lsp_tarball" "$sidecar_gz"; do
      expected="$(awk -v n="$name" '$2 == n || $2 == "*" n {print $1}' "$tmp/sha256sums.txt" | head -n1)"
      [ -n "$expected" ] || continue
      actual="$(sha256sum "$tmp/$name" | awk '{print $1}')"
      if [ "$actual" = "$expected" ]; then
        ok "sha256 verified: ${name}"
      else
        rm -rf "$tmp"
        die "sha256 mismatch for ${name} (expected ${expected}, got ${actual})"
      fi
    done
  else
    warn "sha256sums.txt unavailable - skipping checksum verification"
  fi

  mkdir -p "$INSTALL_DIR"
  tar -xzf "$tmp/$lsp_tarball" -C "$tmp"
  [ -f "$tmp/kmp-lsp" ] || die "kmp-lsp binary missing inside ${lsp_tarball}"
  gunzip -c "$tmp/$sidecar_gz" > "$tmp/kmp-jar-indexer"
  [ -s "$tmp/kmp-jar-indexer" ] || die "kmp-jar-indexer binary missing inside ${sidecar_gz}"

  install -m 0755 "$tmp/kmp-lsp" "$INSTALL_DIR/kmp-lsp"
  install -m 0755 "$tmp/kmp-jar-indexer" "$INSTALL_DIR/kmp-jar-indexer"
  printf '%s\n' "$KMP_LSP_VERSION" > "$INSTALL_DIR/VERSION"

  # Optional: strip when binutils happens to be present. Saves ~13 MB of disk, which
  # matters on a phone, but is never worth installing binutils for.
  if command -v strip >/dev/null 2>&1; then
    strip "$INSTALL_DIR/kmp-lsp" "$INSTALL_DIR/kmp-jar-indexer" 2>/dev/null || true
  fi

  ok "kmp-lsp ${KMP_LSP_VERSION} -> ${INSTALL_DIR}"
}

# Optional environment for the server. The host launches the wrapper with no way to pass
# environment variables, so this file is the only place ANDROID_HOME and friends can be set.
# Created once and never overwritten - edit it freely, re-running the action keeps your changes.
write_env_file() {
  local env_file="${INSTALL_DIR}/env"
  if [ -f "$env_file" ]; then
    ok "env file kept as-is -> ${env_file}"
    return
  fi
  cat > "$env_file" <<KMP_ENV_EOF
# Environment for kmp-lsp, sourced by ${WRAPPER} on every launch.
# Ghost IDE's process launcher cannot pass environment variables to the server, so anything
# kmp-lsp reads from the environment has to be exported here.
#
# Android SDK: kmp-lsp looks at local.properties' sdk.dir first, then ANDROID_HOME, then
# ANDROID_SDK_ROOT. Export it here if your project has no local.properties. It indexes the
# highest platforms/android-XX/android.jar it finds, so the SDK has to be inside the rootfs.
#export ANDROID_HOME=/opt/android-sdk
#
# Gradle cache: *-sources.jar and compiled jars are read from here, which is what makes
# library hover/completion work. Point it at the directory that holds caches/modules-2.
#export GRADLE_USER_HOME=/root/.gradle
#
# Server log: set to a path to get DEBUG logging into that file (kmp-lsp-debug does this too).
#export KMP_LSP_LOG_FILE=/tmp/kmp-lsp.log
KMP_ENV_EOF
  chmod 0644 "$env_file"
  ok "env file -> ${env_file}"
}

# ── 4. launcher ──────────────────────────────────────────────────────────────
# The plugin launches /usr/local/bin/kmp-lsp, so the wrapper owns the stable path and
# the version-independent bits (debug logging); the real binaries stay in /opt/kmp-lsp
# together, because the sidecar is discovered relative to the running executable.
write_wrapper() {
  mkdir -p "$BIN_DIR"
  cat > "$WRAPPER" <<KMP_WRAPPER_EOF
#!/usr/bin/env bash
# kmp-lsp launcher - written by the KMP LSP plugin for Ghost IDE.
# Forwards to the real server in ${INSTALL_DIR}. kmp-lsp finds its jar-indexer sidecar
# next to its own executable, so the two binaries must stay in the same directory.
REAL="${INSTALL_DIR}/kmp-lsp"
if [ ! -x "\$REAL" ]; then
  echo "kmp-lsp: not installed at \$REAL - run 'Install KMP LSP' from the plugin manager." >&2
  exit 127
fi

# ── Android SDK ─────────────────────────────────────────────────────────────
# The host runs this wrapper as a non-interactive process, so the shell startup files the
# AndroidBuilder plugin writes are never read: bash only sources /etc/profile.d/* and ~/.bashrc
# for login/interactive shells. kmp-lsp needs ANDROID_HOME to find the android.jar that makes
# Activity/Context/Compose resolve, so pick the same paths that plugin uses - in the same order,
# and prefer its own env file when it exists so both plugins cannot disagree.
if [ -z "\${ANDROID_HOME:-}" ]; then
  if [ -f /etc/profile.d/androidbuilder-env.sh ]; then
    . /etc/profile.d/androidbuilder-env.sh
  fi
fi
if [ -z "\${ANDROID_HOME:-}" ]; then
  for candidate in "\$HOME/Android/sdk" "\$HOME/.Android/sdk" /opt/android-sdk; do
    if [ -d "\$candidate" ]; then
      export ANDROID_HOME="\$candidate"
      break
    fi
  done
fi
[ -n "\${ANDROID_HOME:-}" ] && export ANDROID_SDK_ROOT="\${ANDROID_SDK_ROOT:-\$ANDROID_HOME}"

# Last, so anything set here wins over the detection above.
[ -f "${INSTALL_DIR}/env" ] && . "${INSTALL_DIR}/env"

# Debug logging: 'touch ${DEBUG_FLAG}' before opening a Kotlin file makes the server
# log at DEBUG into ${LOG_FILE} instead of stderr, which proot swallows. Remove the
# flag to turn it off again.
if [ -e "${DEBUG_FLAG}" ]; then
  export KMP_LSP_LOG_FILE="${LOG_FILE}"
  touch "\$KMP_LSP_LOG_FILE" 2>/dev/null || true
  chmod 666 "\$KMP_LSP_LOG_FILE" 2>/dev/null || true
fi
exec "\$REAL" "\$@"
KMP_WRAPPER_EOF
  chmod 0755 "$WRAPPER"
  ok "launcher -> ${WRAPPER}"

  # Convenience toggle for the flag above, so the user never has to remember the path.
  cat > "$BIN_DIR/kmp-lsp-debug" <<KMP_DEBUG_EOF
#!/usr/bin/env bash
# Toggles kmp-lsp debug logging (kmp-lsp-${KMP_LSP_VERSION}).
case "\${1:-}" in
  on)  touch "${DEBUG_FLAG}"; echo "kmp-lsp debug logging ON  -> ${LOG_FILE}" ;;
  off) rm -f "${DEBUG_FLAG}"; echo "kmp-lsp debug logging OFF" ;;
  *)   if [ -e "${DEBUG_FLAG}" ]; then echo "ON  -> ${LOG_FILE}"; else echo "off"; fi ;;
esac
KMP_DEBUG_EOF
  chmod 0755 "$BIN_DIR/kmp-lsp-debug"
}

# ── 5. smoke tests ───────────────────────────────────────────────────────────
smoke_test() {
  # The server speaks JSON-RPC over stdio by default; closing stdin must make it exit 0.
  if printf '' | timeout 60 "$INSTALL_DIR/kmp-lsp" >/dev/null 2>&1; then
    ok "kmp-lsp starts and shuts down cleanly"
  else
    local rc=$?
    warn "kmp-lsp smoke test exited with status ${rc} - the plugin will still be registered, check the log"
  fi

  # The sidecar answers newline-delimited JSON on stdin/stdout; a shutdown request is
  # enough to prove the native binary runs here.
  if printf '{"shutdown":true}\n' | timeout 30 "$INSTALL_DIR/kmp-jar-indexer" >/dev/null 2>&1; then
    ok "kmp-jar-indexer sidecar responds"
  else
    warn "kmp-jar-indexer sidecar did not respond - library symbols stay limited to the rg fallback"
  fi
}

# ── 6. main ──────────────────────────────────────────────────────────────────
main() {
  install_deps
  detect_platform

  if [ -x "$INSTALL_DIR/kmp-lsp" ] && [ -f "$INSTALL_DIR/VERSION" ] \
     && [ "$(cat "$INSTALL_DIR/VERSION")" = "$KMP_LSP_VERSION" ]; then
    ok "kmp-lsp ${KMP_LSP_VERSION} already installed - refreshing launcher only"
  else
    download_binaries
  fi

  write_wrapper
  write_env_file
  smoke_test

  cat <<KMP_SUMMARY_EOF

kmp-lsp ${KMP_LSP_VERSION} is ready.

  .kt .kts .java .swift  ->  ${WRAPPER}
  server log (debug)     ->  ${LOG_FILE}   (enable: kmp-lsp-debug on)
  real binaries          ->  ${INSTALL_DIR}
  environment overrides  ->  ${INSTALL_DIR}/env

Android SDK: the launcher picks up whatever the AndroidBuilder plugin installed
(\$HOME/Android/sdk by default) and hands it to kmp-lsp as ANDROID_HOME, so android.jar and the
SDK sources are indexed without any extra configuration. Library symbols come from
*-sources.jar in ~/.gradle/caches - run one build with AndroidBuilder once and they appear.
KMP_SUMMARY_EOF
}

main "$@"
