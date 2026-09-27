#!/usr/bin/env bash
# AndroidBuilder for ir - build the project with its own Gradle wrapper:
# find the project that owns ./gradlew, show what it can build, then run the task.
#
#   bash build-apk.sh                              # assembleDebug
#   bash build-apk.sh assembleRelease
#   bash build-apk.sh installDebug /path/to/project
#   bash build-apk.sh tasks                        # only list the tasks
#   bash build-apk.sh assembleDebug /path/to/project '--info --offline'
#
# The 3rd argument is the user's own flag line (--info, --offline, --no-build-cache, -Pkey=value,
# ...). It is split on whitespace and handed to the wrapper verbatim, one flag per word.
set -e

TASK="${1:-assembleDebug}"
PROJECT_HINT="${2:-${ANDROIDBUILDER_PROJECT:-}}"
GRADLE_FLAGS="${3:-${ANDROIDBUILDER_GRADLE_FLAGS:-}}"
AB_SELF="$(basename "$0")"
AB_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# read -a, never eval: the flags are split by the shell itself and then quoted word by word, so
# nothing a user types in the panel can turn into syntax.
AB_FLAGS=()
if [ -n "$GRADLE_FLAGS" ]; then
  read -r -a AB_FLAGS <<<"$GRADLE_FLAGS"
fi

# shellcheck source=gradle-common.sh
if [ ! -f "$AB_SCRIPT_DIR/gradle-common.sh" ]; then
  printf '\033[1;31mERROR: gradle-common.sh is missing next to %s\033[0m\n' "$0" >&2
  exit 1
fi
. "$AB_SCRIPT_DIR/gradle-common.sh"

# `build-apk.sh tasks` is the same as running list-tasks.sh, so the panel and the terminal always
# show the identical list.
case "$TASK" in
  tasks|listTasks|list-tasks|showTasks|--tasks|-t)
    exec bash "$AB_SCRIPT_DIR/list-tasks.sh" ${PROJECT_HINT:+"$PROJECT_HINT"} ${AB_FLAGS[@]+"${AB_FLAGS[@]}"}
    ;;
esac

# ── 0. prerequisites ────────────────────────────────────────────────────────────
ab_check_toolchain

# ── 1. locate the project that owns ./gradlew ───────────────────────────────────
say "building $TASK"
ab_locate_project "$PROJECT_HINT" "$TASK"
cd "$PROJECT_DIR"
echo "  project  : $PROJECT_DIR"
echo "  reason   : $AB_PROJECT_SOURCE"
if [ "$(ab_project_score "$PROJECT_DIR")" -lt 2 ]; then
  warn "$PROJECT_DIR is not an Android application project; '$TASK' may not exist here"
fi
[ -f "$PROJECT_DIR/gradlew" ] || die "no gradlew in $PROJECT_DIR (this is not a Gradle project)"

ab_report_distribution "$PROJECT_DIR"

# ── 2. what can this project build? ─────────────────────────────────────────────
# Shown before the build, the way Android Studio lists its Build menu, so the variant to ask for
# is visible instead of guessed. Reuses a cached listing (see gradle-common.sh) and never fails
# the build: if the task list cannot be produced, the build still runs.
PARSED="$AB_STATE_DIR/tasks.parsed"
if ab_tasks_raw "$PROJECT_DIR"; then
  mkdir -p "$AB_STATE_DIR" 2>/dev/null || true
  ab_parse_tasks "$AB_TASKS_FILE" > "$PARSED"
  if grep -q . "$PARSED" 2>/dev/null; then
    head2 "what this project can build"
    ab_print_tasks "$PARSED" 0
  fi
else
  warn "could not read the task list; running '$TASK' anyway (details in $AB_TASKS_FILE)"
fi

# ── 3. run the wrapper ──────────────────────────────────────────────────────────
# /sdcard is a FUSE mount where Android grants no exec bit, so chmod silently does nothing and
# a direct ./gradlew dies with "Permission denied". The wrapper is a shell script, so it is run
# through bash and addressed by absolute path, never relatively.
#
# --no-daemon: a phone is not a build server. Projects that set org.gradle.daemon=true (many do)
# leave a 2 GB JVM alive for hours after the build, so the next build reuses it, the IDE reports
# nothing useful, and ps fills with orphans. The flag on the command line beats gradle.properties.
# The JVM forked for this one build is still reused *within* the build, so nothing is lost.
bash "$PROJECT_DIR/gradlew" --stop >/dev/null 2>&1 \
  && echo "  stopped any Gradle daemon left over from an earlier build"

# The whole log is kept: on success it is the record of the build, on failure it is the only place
# the compiler's own message exists. tee leaves the output live on screen.
BUILD_LOG="$AB_STATE_DIR/build.log"
mkdir -p "$AB_STATE_DIR" 2>/dev/null || true
say "bash $PROJECT_DIR/gradlew --no-daemon ${AB_FLAGS[*]:-} $TASK"
STARTED=$SECONDS

# The panel is watching for this file, so the install dialog appears when the build ends.
ab_result_write running "$TASK" "" "build started"

# > >(tee ...) keeps the log on screen and on disk while the exit status stays the wrapper's own,
# so "did it compile?" is answered by the command itself rather than by anything piped after it.
# The user's own flags sit between the panel's fixed ones and the task, which is where Gradle wants
# them: the task stays the last word, so a flag that takes a value cannot swallow it.
if bash "$PROJECT_DIR/gradlew" --no-daemon --console=plain ${AB_FLAGS[@]+"${AB_FLAGS[@]}"} "$TASK" > >(tee "$BUILD_LOG") 2>&1; then

  # ── compiled: one block that says so, plus the APK to install ────────────────
  say "APK files"
  APK_LIST="$(find "$PROJECT_DIR" -path '*/build/outputs/apk/*' -name '*.apk' -type f 2>/dev/null | sort)"
  APK_NEWEST="$(printf '%s\n' "$APK_LIST" | grep . | tail -n1 || true)"
  ab_result_write ok "$TASK" "$APK_NEWEST" "APK: $APK_NEWEST"

  ab_report_result ok "$TASK" "$((SECONDS - STARTED))" "$BUILD_LOG" "$PROJECT_DIR"
  if [ -n "$APK_LIST" ]; then
    printf '%s\n' "$APK_LIST"
  else
    echo "  no APK found under */build/outputs/apk"
  fi
  exit 0
fi

# ── failed: stop Gradle, then say what stopped it ───────────────────────────────
# The wrapper already gave up, but a half-finished build can still hold a JVM and the project
# locks, so Gradle is stopped before anything else: nothing of this build is left running.
bash "$PROJECT_DIR/gradlew" --stop >/dev/null 2>&1 || true
ab_report_result fail "$TASK" "$((SECONDS - STARTED))" "$BUILD_LOG" "$PROJECT_DIR"
say "last lines of the build"
tail -n 20 "$BUILD_LOG" 2>/dev/null || true
ab_result_write fail "$TASK" "" "$(ab_failure_reason "$BUILD_LOG" | grep . | head -n1 || true)"
exit 1
