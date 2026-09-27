#!/usr/bin/env bash
# AndroidBuilder for ir - show what the selected project can build, the way Android Studio lists
# its Build menu: every assemble/bundle/install/lint task the project's own build scripts declare.
#
#   bash list-tasks.sh                 # the project's build tasks
#   bash list-tasks.sh --all           # including the long tail (help, wrapper, publishing)
#   bash list-tasks.sh /path/to/project
#   bash list-tasks.sh /path/to/project --info
#
# Flags that start with a dash belong to Gradle and are forwarded to the wrapper; anything else is
# the project. --info and --stacktrace are what make a broken build script explain itself here.
set -e

AB_SELF="$(basename "$0")"
TASK="${1:-}"
PROJECT_HINT=""
AB_FLAGS=()
for argument in "$@"; do
  case "$argument" in
    -*) AB_FLAGS+=("$argument") ;;
    *) PROJECT_HINT="$argument" ;;
  esac
done

AB_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=gradle-common.sh
if [ ! -f "$AB_SCRIPT_DIR/gradle-common.sh" ]; then
  printf '\033[1;31mERROR: gradle-common.sh is missing next to %s\033[0m\n' "$0" >&2
  exit 1
fi
. "$AB_SCRIPT_DIR/gradle-common.sh"

ab_check_toolchain
say "Gradle tasks"

ab_locate_project "$PROJECT_HINT" "tasks"
cd "$PROJECT_DIR"
echo "  project  : $PROJECT_DIR"
echo "  reason   : $AB_PROJECT_SOURCE"
[ -f "$PROJECT_DIR/gradlew" ] || die "no gradlew in $PROJECT_DIR (this is not a Gradle project)"
if [ "$(ab_project_score "$PROJECT_DIR")" -lt 2 ]; then
  warn "$PROJECT_DIR is not an Android application project; it may have no APK tasks"
fi

# A leftover daemon would still be holding memory from the last build.
bash "$PROJECT_DIR/gradlew" --stop >/dev/null 2>&1 || true

if ab_tasks_raw "$PROJECT_DIR" ${AB_FLAGS[@]+"${AB_FLAGS[@]}"}; then
  PARSED="$AB_STATE_DIR/tasks.parsed"
  mkdir -p "$AB_STATE_DIR" 2>/dev/null || true
  ab_parse_tasks "$AB_TASKS_FILE" > "$PARSED"
  FULL=0
  case " $* " in *" --all "*) FULL=1 ;; esac
  ab_print_tasks "$PARSED" "$FULL"

  say "build one of them"
  printf '    bash %s/build-apk.sh <task>%s\n' "$AB_SCRIPT_DIR" "${PROJECT_HINT:+ $PROJECT_HINT}"
  printf '    bash %s/build-apk.sh tasks%s\n' "$AB_SCRIPT_DIR" "${PROJECT_HINT:+ $PROJECT_HINT}"
  exit 0
fi

# `tasks` configures the project, so a broken build script fails here first. Show why, since the
# real Gradle output is still sitting in the file.
say "Gradle could not configure the project"
tail -n 25 "$AB_TASKS_FILE" 2>/dev/null || true
echo
warn "the full output is in $AB_TASKS_FILE"
die "fix the build script (or install what it is missing) and run this again"
