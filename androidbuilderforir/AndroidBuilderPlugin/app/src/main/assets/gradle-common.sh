#!/usr/bin/env bash
# AndroidBuilder for ir - shared shell library: locate the project, check the toolchain, and list
# the project's Gradle tasks the way Android Studio's Build menu does.
#
# It is sourced, never executed: build-apk.sh and list-tasks.sh both `source` it from their own
# directory, so the two commands always agree on which project they are talking about.
# shellcheck shell=bash

AB_SCRIPT_DIR="${AB_SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)}"
AB_STATE_DIR="${ANDROIDBUILDER_STATE_DIR:-${XDG_CONFIG_HOME:-$HOME/.config}/androidbuilder}"
AB_LAST_PROJECT_FILE="$AB_STATE_DIR/last-project"
AB_TASKS_FILE="$AB_STATE_DIR/tasks.txt"
# Listing tasks configures the whole build (every module's build scripts run), which on a phone is
# not free. The result only changes when the build files do, so it is reused for this long.
AB_TASKS_TTL="${ANDROIDBUILDER_TASKS_TTL:-1800}"

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARNING: %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
head2() { printf '\n\033[1;37m  %s\033[0m\n' "$*"; }

# ── toolchain ───────────────────────────────────────────────────────────────────
# Resolves the SDK/JDK the same way for every entry point. Exports ANDROID_HOME / ANDROID_SDK_ROOT.
ab_check_toolchain() {
  command -v java >/dev/null 2>&1 \
    || die "no JVM on PATH. Run 'Install JDK 17' from the AndroidBuilder panel first."

  if [ -z "${ANDROID_HOME:-}" ]; then
    local candidate
    for candidate in "$HOME/Android/sdk" "$HOME/.Android/sdk" /opt/android-sdk; do
      if [ -d "$candidate" ]; then
        ANDROID_HOME="$candidate"
        break
      fi
    done
  fi
  [ -n "${ANDROID_HOME:-}" ] \
    || die "ANDROID_HOME is not set. Run 'Install Android SDK' from the AndroidBuilder panel first."
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

  if [ ! -d "$ANDROID_HOME/build-tools" ] || [ -z "$(ls -A "$ANDROID_HOME/build-tools" 2>/dev/null)" ]; then
    die "$ANDROID_HOME has no build-tools. Run 'Install Android SDK' from the AndroidBuilder panel first."
  fi
  echo "  ANDROID_HOME: $ANDROID_HOME"
  echo "  build-tools : $(ls -1 "$ANDROID_HOME/build-tools" | tr '\n' ' ')"
}

# Reports whether the wrapper's own Gradle distribution is already unpacked, so it is clear up
# front whether this is about to pull ~140 MB or reuse what is on disk.
ab_report_distribution() {
  local gradle_home wrapper_props dist_url dist_name
  gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  wrapper_props="$1/gradle/wrapper/gradle-wrapper.properties"
  dist_url=""
  [ -f "$wrapper_props" ] && dist_url="$(sed -n 's/^distributionUrl=//p' "$wrapper_props" | tr -d '\\')"
  if [ -n "$dist_url" ]; then
    dist_name="$(basename "$dist_url")"
    dist_name="${dist_name%.zip}"
    if ls "$gradle_home"/wrapper/dists/"$dist_name"/*/"$dist_name.zip.ok" >/dev/null 2>&1; then
      echo "  gradle    : $dist_name already installed, no download"
    else
      warn "gradle $dist_name is not installed yet; this first build downloads it from $dist_url"
    fi
  else
    warn "no distributionUrl in $wrapper_props, cannot tell whether Gradle is already installed"
  fi
}

# ── finding the project ─────────────────────────────────────────────────────────
# This device holds dozens of Gradle projects, so "the first ./gradlew find turns up" is not a
# project picker: it happily builds an unrelated LSP plugin. The project is therefore taken from,
# in order: the path the panel passed, the path this toolchain last used, the current directory
# (or the Gradle root above it), and only then a scan - and the scan refuses to guess between two
# equally plausible projects.

# Turns anything the user typed - a file, a module folder, the project root - into the directory
# that actually owns the wrapper.
ab_resolve_project() {
  local input="$1" dir="" up=0
  [ -n "$input" ] || return 1
  [ -f "$input" ] && input="$(dirname "$input")"
  [ -d "$input" ] || return 1
  # An absolute path, so a relative argument is not tested against the wrong parent chain.
  dir="$(cd "$input" 2>/dev/null && pwd)" || return 1

  # The directory itself first, then the roots above it: a module folder such as .../MyApp/app and
  # a subdirectory such as .../MyApp/app/src both belong to the project that owns the wrapper.
  while [ "$up" -lt 5 ] && [ "$dir" != "/" ] && [ "$dir" != "." ]; do
    if [ -f "$dir/gradlew" ]; then
      printf '%s\n' "$dir"
      return 0
    fi
    dir="$(dirname "$dir")"
    up=$((up + 1))
  done
  return 1
}

# 3 = an Android application (a module applies com.android.application, so assemble* exists),
# 2 = an Android-shaped project, 1 = a plain Gradle project, 0 = not a Gradle project at all.
ab_project_score() {
  local dir="$1" file
  [ -f "$dir/gradlew" ] || { printf '0'; return 0; }
  [ -f "$dir/settings.gradle" ] || [ -f "$dir/settings.gradle.kts" ] || { printf '1'; return 0; }
  for file in "$dir/build.gradle" "$dir/build.gradle.kts" "$dir"/*/build.gradle "$dir"/*/build.gradle.kts; do
    if [ -f "$file" ] && grep -q 'com\.android\.application' "$file" 2>/dev/null; then
      printf '3'
      return 0
    fi
  done
  [ -d "$dir/app" ] && { printf '2'; return 0; }
  printf '1'
}

ab_remember_project() {
  mkdir -p "$AB_STATE_DIR" 2>/dev/null || return 0
  printf '%s\n' "$1" > "$AB_LAST_PROJECT_FILE" 2>/dev/null || true
}

# Sets PROJECT_DIR and AB_PROJECT_SOURCE. $1 is the hint from the panel (may be empty), $2 is the
# task name, only used to make the "pass the path as the 2nd argument" hint concrete.
ab_locate_project() {
  local hint="$1" task="${2:-build}" ranked best ties tie_count total wrapper dir root roots remembered
  local scan_roots=""
  PROJECT_DIR=""

  if [ -n "$hint" ]; then
    PROJECT_DIR="$(ab_resolve_project "$hint" || true)"
    [ -n "$PROJECT_DIR" ] \
      || die "no gradlew in '$hint', and no Gradle project above it either (fix the project path in the AndroidBuilder panel)"
    ab_remember_project "$PROJECT_DIR"
    AB_PROJECT_SOURCE="the project path given to the script"
    return 0
  fi

  # The remembered project is the *content* of that file, not the file: last-project lives in the
  # state dir, which is not a Gradle project, so resolving the file itself always came back empty
  # and the scan below had to guess between every project on the device.
  remembered="$(head -n1 "$AB_LAST_PROJECT_FILE" 2>/dev/null || true)"
  PROJECT_DIR="$(ab_resolve_project "$remembered" 2>/dev/null || true)"
  if [ -n "$PROJECT_DIR" ]; then
    AB_PROJECT_SOURCE="the last project built here"
    return 0
  fi

  PROJECT_DIR="$(ab_resolve_project "$PWD" || true)"
  if [ -n "$PROJECT_DIR" ]; then
    ab_remember_project "$PROJECT_DIR"
    AB_PROJECT_SOURCE="the current directory"
    return 0
  fi

  # Nothing was asked for explicitly, so scan - and only accept an unambiguous answer.
  mkdir -p "$AB_STATE_DIR" 2>/dev/null || true
  for root in "$HOME" /sdcard/AndroidIDEProjects /sdcard/apk /sdcard/Documents /sdcard; do
    [ -d "$root" ] || continue
    case " $scan_roots " in *" $root "*) ;; *) scan_roots="$scan_roots $root" ;; esac
  done
  for root in $scan_roots; do
    find "$root" -maxdepth 5 \
      -name build -type d -prune -o \
      -name .gradle -type d -prune -o \
      -name gradlew -type f -print 2>/dev/null
  done | sort -u > "$AB_STATE_DIR/.candidates" 2>/dev/null || true

  if [ ! -s "$AB_STATE_DIR/.candidates" ]; then
    rm -f "$AB_STATE_DIR/.candidates" 2>/dev/null || true
    die "no Gradle project found. Pass the project path as the 2nd argument, or run this from inside the project"
  fi

  ranked="$(while IFS= read -r wrapper; do
    [ -n "$wrapper" ] || continue
    dir="$(dirname "$wrapper")"
    printf '%s\t%s\t%s\n' "$(ab_project_score "$dir")" "$(stat -c %Y "$dir" 2>/dev/null || echo 0)" "$dir"
  done < "$AB_STATE_DIR/.candidates" | sort -t"$(printf '\t')" -k1,1nr -k2,2nr || true)"
  rm -f "$AB_STATE_DIR/.candidates" 2>/dev/null || true

  total="$(printf '%s\n' "$ranked" | grep -c . || true)"
  best="$(printf '%s\n' "$ranked" | head -n1 | cut -f1)"
  # 3 and 2 both mean "this can produce an APK"; anything lower is only a last resort.
  ties="$(printf '%s\n' "$ranked" | awk -F'\t' -v best="$best" '$1 == best {print $3}')"
  tie_count="$(printf '%s\n' "$ties" | grep -c . || true)"

  if [ "$tie_count" -eq 1 ]; then
    PROJECT_DIR="$ties"
    ab_remember_project "$PROJECT_DIR"
    AB_PROJECT_SOURCE="auto-detected, $total Gradle project(s) on this device"
    return 0
  fi
  if [ "$best" -lt 2 ]; then
    # No Android project at all, and several plain Gradle ones: take the freshest rather than an
    # arbitrary directory order, and say so out loud.
    PROJECT_DIR="$ties"
    ab_remember_project "$PROJECT_DIR"
    warn "no Android project among $total Gradle projects; using the most recently touched: $PROJECT_DIR"
    AB_PROJECT_SOURCE="auto-detected (no Android project found)"
    return 0
  fi
  say "several Android projects found - say which one:"
  printf '    %s\n' "$ties"
  die "pass it as the 2nd argument:  bash $AB_SELF $task /path/to/project     (or export ANDROIDBUILDER_PROJECT=/path/to/project)"
}

# ── the task list, the way Android Studio's Build menu shows it ─────────────────
# `gradlew tasks --all` configures every module (no code is compiled) and prints one
# "name - description" line per task, subproject tasks carrying a "app:" style prefix. That
# single invocation is the whole source of truth: variants, flavors and module names all come
# from the project's own build scripts, so nothing here is guessed.
ab_tasks_raw() {
  local project="$1" output stamp now age ok=0
  shift
  output="$AB_TASKS_FILE"
  stamp="$AB_STATE_DIR/tasks.stamp"

  mkdir -p "$AB_STATE_DIR" 2>/dev/null || true
  now="$(date +%s 2>/dev/null || echo 0)"

  # The build files decide when the list changes; their newest mtime is the cache key. The user's
  # own flags are part of the key too: --offline and --refresh-dependencies read a different Gradle
  # state, and one list must never be served for the other.
  local key="$project:$*"
  for file in "$project/settings.gradle" "$project/settings.gradle.kts" "$project/build.gradle" \
              "$project/build.gradle.kts" "$project/gradle.properties"; do
    [ -f "$file" ] && key="$key:$(stat -c %Y "$file" 2>/dev/null || echo 0)"
  done

  if [ -f "$output" ] && [ -f "$stamp" ] && [ "$(cat "$stamp" 2>/dev/null)" = "$key" ]; then
    age=$((now - $(stat -c %Y "$output" 2>/dev/null || echo 0)))
    if [ "$age" -ge 0 ] && [ "$age" -lt "$AB_TASKS_TTL" ]; then
      ok=1
    fi
  fi

  if [ "$ok" -eq 0 ]; then
    say "reading the project's Gradle tasks (this configures the build, nothing is compiled)"
    # --no-daemon: see build-apk.sh. Output goes to a file so the phone's console is not flooded
    # with a few hundred unfiltered lines before the pretty list.
    if (cd "$project" && bash "$project/gradlew" --no-daemon --console=plain -q tasks --all "$@") \
        > "$output" 2>&1; then
      printf '%s\n' "$key" > "$stamp" 2>/dev/null || true
      return 0
    fi
    return 1
  fi
  return 0
}

# One task per line: "name<TAB>description". Gradle wraps long descriptions in rich mode, so any
# indented continuation line is dropped rather than mistaken for a task.
ab_parse_tasks() {
  sed -n 's/^\([A-Za-z0-9_:.-]\{1,\}\) - \(.*\)$/\1\t\2/p' "$1" 2>/dev/null \
    | grep -v -E '^(To see|Run |Tasks runnable)' || true
}

ab_task_group() {
  case "$1" in
    *:assemble*|assemble*|*:bundle*|bundle*|*:package*|package*)
      printf 'Build outputs (APK / AAB)' ;;
    *:install*|install*|*:uninstall*|uninstall*|*:push*|*:install-*push*)
      printf 'Install / uninstall on a device' ;;
    clean*|*:clean*|rebuild)
      printf 'Clean / rebuild' ;;
    *:lint*|lint*|*:check*|check*|*:analyze*|analyze*|*:ktlint*)
      printf 'Checks (lint, analyse)' ;;
    *:test*|test*|*Test|connected*|*:connectedAndroidTest*)
      printf 'Tests' ;;
    dependencies|*:dependencies*|dependencyInsight|*:dependencyInsight*)
      printf 'Dependencies' ;;
    help|init|projects|model|wrapper|*:wrapper|javaToolchains|outgoingVariants|resolvableConfigurations)
      printf 'Help and diagnostics' ;;
    *)
      printf 'Other' ;;
  esac
}

# What the task leaves behind, so the list says the useful thing: which file to look for.
ab_task_artifact() {
  case "$1" in
    *:assemble*|assemble*|*:package*|package*) printf 'APK' ;;
    *:bundle*|bundle*) printf 'AAB' ;;
    *:install*|install*) printf 'device' ;;
    *:uninstall*|uninstall*) printf 'device' ;;
    clean*|*:clean*|rebuild) printf '' ;;
    *:lint*|lint*) printf 'report' ;;
    *) printf '' ;;
  esac
}

# Prints the menu. $1 parsed task file, $2 "1" to also list the long tail of misc tasks.
ab_print_tasks() {
  local parsed="$1" full="$2" line name description group artifact
  local -a groups=(
    'Build outputs (APK / AAB)'
    'Install / uninstall on a device'
    'Clean / rebuild'
    'Tests'
    'Checks (lint, analyse)'
    'Dependencies'
    'Help and diagnostics'
  )
  local modules total=0 other=0

  modules="$(cut -f1 "$parsed" 2>/dev/null | grep ':' | cut -d: -f1 | sort -u | tr '\n' ' ')"
  total="$(grep -c . "$parsed" 2>/dev/null || true)"
  [ -n "$total" ] || total=0
  [ -n "$modules" ] && echo "  modules   : $modules"
  echo "  tasks     : $total"

  for group in "${groups[@]}"; do
    local body=""
    while IFS=$'\t' read -r name description; do
      [ -n "$name" ] || continue
      [ "$(ab_task_group "$name")" = "$group" ] || continue
      artifact="$(ab_task_artifact "$name")"
      # $( ) strips trailing newlines, so the line break is appended outside it; without this the
      # whole group collapses onto one very long line.
      if [ -n "$artifact" ]; then
        body+="$(printf '    \033[1m%-34s\033[0m \033[32m-> %s\033[0m  %s' "$name" "$artifact" "$description")"$'\n'
      else
        body+="$(printf '    %-34s %s' "$name" "$description")"$'\n'
      fi
    done < "$parsed"
    if [ -n "$body" ]; then
      printf '\n\033[1;36m  %s\033[0m\n' "$group"
      printf '%s' "$body"
    fi
  done

  other="$(while IFS= read -r name; do
    [ -n "$name" ] || continue
    case "$(ab_task_group "$name")" in
      'Build outputs (APK / AAB)'|'Install / uninstall on a device'|'Clean / rebuild'|Tests|'Checks (lint, analyse)'|Dependencies|'Help and diagnostics') ;;
      *) printf '%s\n' "$name" ;;
    esac
  done < "$parsed" | wc -l)"

  if [ "$full" != "1" ] && [ "$other" -gt 0 ]; then
    printf '\n\033[1;90m  ...and %s more (help, wrapper, publishing, code quality)\n' "$other"
    printf '      run with --all to list every task\033[0m\n'
  elif [ "$other" -gt 0 ]; then
    printf '\n\033[1;36m  Other\033[0m\n'
    while IFS= read -r name; do
      [ -n "$name" ] || continue
      case "$(ab_task_group "$name")" in
        'Build outputs (APK / AAB)'|'Install / uninstall on a device'|'Clean / rebuild'|Tests|'Checks (lint, analyse)'|Dependencies|'Help and diagnostics') ;;
        *) printf '    %s\n' "$name" ;;
      esac
    done < "$parsed"
  fi

  [ "$total" -gt 0 ] || warn "Gradle reported no tasks for $PROJECT_DIR"
}

# ── the result marker ───────────────────────────────────────────────────────────
# The build itself runs in the proot terminal, where the panel cannot see it, so the outcome is
# left in a file both sides can reach: /ghostide/files is the host app's own files directory, bind
# mounted into every terminal session (the same place the plugin stages its scripts into). The
# plugin polls this file and turns a finished build into the install dialog.
AB_RESULT_FILE="${ANDROIDBUILDER_RESULT_FILE:-/ghostide/files/androidbuilder/last-result}"

# The marker, or the state directory when the host directory is not writable (a bare terminal
# session on a device where the plugin has never run).
ab_result_path() {
  local directory
  directory="$(dirname "$AB_RESULT_FILE")"
  if mkdir -p "$directory" 2>/dev/null && [ -w "$directory" ]; then
    printf '%s\n' "$AB_RESULT_FILE"
    return 0
  fi
  printf '%s\n' "$AB_STATE_DIR/last-result"
}

# ab_result_write <running|ok|fail> <task> <apk> <summary>
ab_result_write() {
  local status="$1" task="$2" apk="$3" summary="$4" file temporary now
  file="$(ab_result_path)"
  temporary="$file.tmp.$$"
  now="$(date +%s 2>/dev/null || echo 0)"
  # 'at' is written last on purpose: the plugin only trusts a marker that has one, so a file that
  # is caught mid-write is read as "no result yet" instead of as a finished build.
  {
    printf 'status=%s\n' "$status"
    printf 'task=%s\n' "$task"
    if [ -n "$apk" ]; then
      printf 'apk=%s\n' "$apk"
    fi
    if [ -n "$summary" ]; then
      printf 'summary=%s\n' "$(printf '%s' "$summary" | tr '\n\r' '  ' | cut -c1-300)"
    fi
    printf 'at=%s\n' "$now"
  } > "$temporary" 2>/dev/null || return 0
  mv -f "$temporary" "$file" 2>/dev/null || rm -f "$temporary" 2>/dev/null || true
  return 0
}

# ── the result box ──────────────────────────────────────────────────────────────
# Android Studio ends a build with a window that says what happened and where the artifact is; the
# terminal equivalent is dialog(1). It is used only when there is a real terminal, so a piped or
# captured run still gets the same information as plain text.
ab_dialog() {
  local title="$1" text="$2"
  [ -t 1 ] || return 0
  # The panel started this build: it watches the result file and puts its own install dialog up
  # when the build ends, so a window here would be a second one for the same event - and it would
  # hold the terminal open until somebody dismissed it. The outcome is still printed above.
  [ "${ANDROIDBUILDER_PLUGIN_UI:-0}" = "1" ] && return 0
  command -v dialog >/dev/null 2>&1 || return 0
  dialog --title "$title" --ok-label "OK" --msgbox "$text" 0 0 >/dev/null 2>&1 || true
}

# A rule the width of the message above it, so the result reads as one block.
ab_rule() {
  printf '%*s\n' "${#1}" '' | tr ' ' '-'
}

# $1 ok|fail, $2 task, $3 seconds, $4 log file, $5 project.
# Prints the outcome, then puts it in a dialog box.
ab_report_result() {
  local outcome="$1" task="$2" seconds="$3" log="$4" project="$5"
  local summary="" detail="" apk size

  if [ "$outcome" = "ok" ]; then
    apk="$(find "$project" -path '*/build/outputs/apk/*' -name '*.apk' -type f 2>/dev/null | sort | tail -n1)"
    summary="BUILD SUCCEEDED - $task finished in ${seconds}s"
    detail="Task ran."
    if [ -n "$apk" ]; then
      size="$(du -h "$apk" 2>/dev/null | cut -f1)"
      detail="APK: $apk"
      [ -n "$size" ] && detail="$detail
size: $size"
    else
      detail="Task ran, but no APK was found under */build/outputs/apk."
    fi
  else
    summary="BUILD FAILED - $task gave up after ${seconds}s"
    detail="$(ab_failure_reason "$log")"
  fi

  printf '\n\033[1;37m%s\033[0m\n' "$(ab_rule "$summary")"
  if [ "$outcome" = "ok" ]; then
    printf '\033[1;32m%s\033[0m\n' "$summary"
    printf '%s\n' "$detail" | sed 's/^/  /'
  else
    printf '\033[1;31m%s\033[0m\n' "$summary"
    printf '%s\n' "$detail" | sed 's/^/  /'
    printf '\033[1;33m  Gradle stopped; nothing of this build is left running.\033[0m\n'
    printf '\033[1;90m  full log: %s\033[0m\n' "$log"
  fi
  printf '\033[1;37m%s\033[0m\n' "$(ab_rule "$summary")"

  ab_dialog "$summary" "$detail"
  return 0
}

# Pulls the part of Gradle's output that actually says why it stopped: the task it was on and the
# "* What went wrong" block, or the first compiler complaint when the build never got that far.
ab_failure_reason() {
  local log="$1" failed what compiler
  failed="$(grep -m1 -o "Execution failed for task '[^']*'" "$log" 2>/dev/null \
    | sed "s/Execution failed for task '//; s/'$//" || true)"
  what="$(awk '/^\* What went wrong:/{flag=1; next} /^\* Try:/{flag=0} flag' "$log" 2>/dev/null \
    | grep -v '^[[:space:]]*$' | head -n5 || true)"
  compiler="$(grep -m4 -E '^e: |error: |^\s*ERROR:' "$log" 2>/dev/null || true)"

  [ -n "$failed" ] && printf 'Failed task: %s\n' "$failed"
  if [ -n "$what" ]; then
    printf '\n%s\n' "$what"
  elif [ -n "$compiler" ]; then
    printf '\n%s\n' "$compiler"
  else
    # Configuration itself failed, so there is no task to blame - the tail is all there is.
    printf '\n%s\n' "$(tail -n6 "$log" 2>/dev/null)"
  fi
}
