#!/usr/bin/env bash
# AndroidBuilder for ir - stop a build that is still running.
#
# The build lives in one terminal session and this runs in another, but proot sessions share the
# process list, so a plain pkill is enough. Three things are looked for: the script itself (which
# owns the log and the result file), the JVM Gradle runs in, and any leftover daemon. Whatever was
# running is reported, and the result file is set to "cancelled" so the panel stops waiting for a
# build that is no longer there.
set -e

AB_STATE_DIR="${ANDROIDBUILDER_STATE_DIR:-/ghostide/files/androidbuilder}"
AB_RESULT_FILE="${ANDROIDBUILDER_RESULT_FILE:-$AB_STATE_DIR/last-result}"
TASK="${1:-unknown}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
# Only this plugin's own build, and only a real invocation of it: the pattern is anchored on the
# executable, so a shell that merely mentions the path (a log line, a wrapper, this script's own
# command) is never a match.
BUILD_PATTERN='^[^ ]*bash [^ ]*build-apk\.sh'
JVM_PATTERN='^[^ ]*/java .*gradle-wrapper\.jar'

kill_pattern() {
  local pattern="$1" label="$2" ids id found=0 cmdline
  ids="$(pgrep -f "$pattern" 2>/dev/null || true)"
  for id in $ids; do
    [ "$id" = "$$" ] && continue
    [ "$id" = "$PPID" ] && continue
    cmdline="$(tr '\0' ' ' < "/proc/$id/cmdline" 2>/dev/null || true)"
    case "$cmdline" in
      *stop-build*) continue ;;
    esac
    if kill -TERM "$id" 2>/dev/null; then
      found=$((found + 1))
    fi
  done
  if [ "$found" -gt 0 ]; then
    say "stopped $found $label"
  else
    printf '  nothing to stop: %s\n' "$label"
  fi
}

# 1. the build script, so it writes no result of its own after we are done
kill_pattern "$BUILD_PATTERN" "build script"

# 2. the JVM Gradle runs in
kill_pattern "$JVM_PATTERN" "Gradle JVM"
kill_pattern 'org\.gradle\.launcher' "Gradle launcher"

# 3. a daemon that survived the kill
if command -v gradle >/dev/null 2>&1; then
  gradle --stop >/dev/null 2>&1 || true
fi

sleep 1

# 4. what is still standing: TERM is not always enough for a JVM mid-compile
for id in $(pgrep -f "$JVM_PATTERN" 2>/dev/null || true); do
  [ "$id" = "$$" ] && continue
  kill -KILL "$id" 2>/dev/null || true
done

# 5. tell the panel, so it stops waiting for a build that no longer runs
if [ -d "$(dirname "$AB_RESULT_FILE")" ]; then
  cat > "$AB_RESULT_FILE" <<EOF
status=cancelled
task=$TASK
apk=
summary=cancelled by the panel
at=$(date +%s)
EOF
  say "result file set to cancelled: $AB_RESULT_FILE"
else
  warn "cannot write $AB_RESULT_FILE - the panel may keep waiting"
fi

say "done, nothing of this build is left running"
