# kmp-env.sh - where kmp-lsp finds the Android SDK and the Gradle cache.
#
# Sourced, never executed. It only exports; it prints nothing and fails at nothing, because the
# two things it looks for are optional for the server and fatal for the workspace preparation.
#
# kmp-lsp resolves library sources in this order, and both steps have to work before a Java or
# Kotlin file can resolve `Activity`, `Context` or a single AndroidX class:
#   1. sdk.dir in the project's local.properties   (a project Android Studio opened has one)
#   2. $ANDROID_HOME
#   3. $ANDROID_SDK_ROOT
# The Android SDK lives inside the rootfs, at $HOME/Android/sdk, and the host's process launcher
# cannot pass environment variables - so this file is where the connection is made. The candidate
# list is the same one install-sdk.sh writes into, plus the absolute /root, because a
# non-interactive process is not guaranteed to inherit HOME.
#
# GRADLE_USER_HOME matters just as much: since v0.21 kmp-lsp mounts every *-sources.jar it finds
# under <gradle home>/caches in memory at startup, which is where library hover docs and
# go-to-definition into library code come from. A build has to have populated it at least once.

kmp_detect_android_sdk() {
  [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ] && return 0

  local candidate
  for candidate in \
      "${KMP_SDK_HINT:-}" \
      "$HOME/Android/sdk" \
      /root/Android/sdk \
      "$HOME/.Android/sdk" \
      /opt/android-sdk; do
    [ -n "$candidate" ] || continue
    if [ -d "$candidate" ]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done

  # The shell startup files the AndroidBuilder plugin writes. A non-interactive bash reads
  # /etc/profile.d/*, so these are the most reliable answer when they exist.
  if [ -z "${ANDROID_HOME:-}" ] && [ -f /etc/profile.d/androidbuilder-env.sh ]; then
    # shellcheck source=/dev/null
    . /etc/profile.d/androidbuilder-env.sh
  fi

  [ -n "${ANDROID_HOME:-}" ] && export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
  return 0
}

kmp_detect_gradle_home() {
  if [ -z "${GRADLE_USER_HOME:-}" ] || [ ! -d "${GRADLE_USER_HOME}" ]; then
    for candidate in "${KMP_GRADLE_HINT:-}" "$HOME/.gradle" /root/.gradle; do
      if [ -n "$candidate" ] && [ -d "$candidate" ]; then
        export GRADLE_USER_HOME="$candidate"
        break
      fi
    done
  fi
  return 0
}

kmp_detect_java_home() {
  [ -n "${JAVA_HOME:-}" ] && [ -d "${JAVA_HOME}" ] && return 0
  if [ -f /etc/profile.d/androidbuilder-java.sh ]; then
    # shellcheck source=/dev/null
    . /etc/profile.d/androidbuilder-java.sh
  fi
  return 0
}

# The highest installed platform: that is the android.jar kmp-lsp indexes for the java.* and
# android.* classes every Java file resolves against.
kmp_android_jar() {
  [ -n "${ANDROID_HOME:-}" ] || return 1
  local newest="" jar platform version best=0
  for jar in "$ANDROID_HOME"/platforms/*/android.jar; do
    [ -f "$jar" ] || continue
    platform="$(basename "$(dirname "$jar")")"
    version="${platform#android-}"
    case "$version" in
      ''|*[!0-9]*) continue ;;
    esac
    # Numbers, not strings: "android-9" must lose to "android-36".
    if [ "$((10#$version))" -gt "$best" ]; then
      best="$((10#$version))"
      newest="$jar"
    fi
  done
  [ -n "$newest" ] || return 1
  printf '%s\n' "$newest"
}

kmp_detect_android_sdk
kmp_detect_gradle_home
kmp_detect_java_home
