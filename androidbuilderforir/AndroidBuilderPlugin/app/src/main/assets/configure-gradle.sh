#!/usr/bin/env bash
# AndroidBuilder for ir - make Gradle resolve Google's Maven artifacts, from wherever it can.
#
# Google Maven (dl.google.com / maven.google.com) is where AGP, the AndroidX artifacts and the
# build-tools metadata come from, and on a sanctioned network it answers 403 or 404 instead of the
# file. This script asks the host first: if it answers with real content the project's own google()
# repositories are left alone, otherwise they are rewritten to the Iranian mirror maven.myket.ir
# (and the mirror is put first for plugin and buildscript resolution).
#
# Repositories are rewritten rather than added, because adding breaks every project that sets
# RepositoriesMode.FAIL_ON_PROJECT_REPOS.
#
#   ANDROIDBUILDER_MAVEN_MIRROR   the mirror to use when Google is blocked
#   ANDROIDBUILDER_MIRROR_MODE    auto (default, probe the host) | always | never
set -e

MAVEN_MIRROR="${ANDROIDBUILDER_MAVEN_MIRROR:-https://maven.myket.ir}"
MIRROR_MODE="${ANDROIDBUILDER_MIRROR_MODE:-auto}"
GRADLE_USER_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GOOGLE_PROBE="https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# shellcheck source=net-route.sh
[ -f "$SCRIPT_DIR/net-route.sh" ] && . "$SCRIPT_DIR/net-route.sh"

# ── 1. can dl.google.com be reached from this network? ───────────────────────────
USE_MIRROR=0
case "$MIRROR_MODE" in
  always) USE_MIRROR=1; say "mirror mode: always ($MAVEN_MIRROR)" ;;
  never) say "mirror mode: never, Google's own hosts are kept" ;;
  *)
    if command -v ab_route >/dev/null 2>&1; then
      say "asking dl.google.com whether it answers from here"
      GOOGLE_ROUTE="$(ab_route "$GOOGLE_PROBE" '<metadata')"
    else
      GOOGLE_ROUTE="unknown (net-route.sh is missing)"
    fi
    case "$GOOGLE_ROUTE" in
      direct*)
        say "dl.google.com answers normally here ($GOOGLE_ROUTE): Google's own repositories are kept"
        ;;
      *)
        USE_MIRROR=1
        say "dl.google.com is not usable here ($GOOGLE_ROUTE) - sending Google Maven through $MAVEN_MIRROR"
        ;;
    esac
    ;;
esac

mkdir -p "$GRADLE_USER_DIR"
INIT_SCRIPT="$GRADLE_USER_DIR/init.gradle"

# ── 2. the init script ───────────────────────────────────────────────────────────
say "writing $INIT_SCRIPT"
if [ "$USE_MIRROR" = "1" ]; then
  cat > "$INIT_SCRIPT" <<'GRADLE_EOF'
// AndroidBuilder for ir - resolve Google's Maven artifacts through the Iranian mirror.
// Only Google's own hosts are remapped: the Gradle Plugin Portal and Maven Central are not
// mirrored, so plugins and library artifacts keep resolving from their real servers.
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

def irMirror = '__IR_MIRROR__'
def irHosts = ['dl.google.com', 'maven.google.com'] as Set

// Rewrites the repositories a build already declares; adding repositories instead would fail
// every project that uses RepositoriesMode.FAIL_ON_PROJECT_REPOS.
def remapGoogle = { repos ->
    repos.all { repo ->
        if (repo instanceof MavenArtifactRepository) {
            def host = repo.url?.host
            if (host != null && irHosts.contains(host)) {
                repo.url = irMirror
            }
        }
    }
}

// The mirror is only *prepended* where adding repositories is always allowed.
def prependMirror = { repos ->
    if (repos.findByName('irMyketMirror') != null) {
        return
    }
    repos.maven { it.name = 'irMyketMirror'; it.url = irMirror }
}

settingsEvaluated { settings ->
    prependMirror(settings.pluginManagement.repositories)
    remapGoogle(settings.pluginManagement.repositories)
    try {
        remapGoogle(settings.dependencyResolutionManagement.repositories)
    } catch (Exception ignored) {
    }
}

allprojects { project ->
    prependMirror(project.buildscript.repositories)
    remapGoogle(project.buildscript.repositories)
    remapGoogle(project.repositories)
}
GRADLE_EOF

  sed -i "s#__IR_MIRROR__#$MAVEN_MIRROR#g" "$INIT_SCRIPT"
  grep -q "$MAVEN_MIRROR" "$INIT_SCRIPT" || die "could not substitute the mirror url into $INIT_SCRIPT"
else
  # Nothing to remap: a minimal script that only carries the settings below, so an existing mirror
  # from a previous run does not stay behind and keep a reachable network on the slow path.
  cat > "$INIT_SCRIPT" <<'GRADLE_EOF'
// AndroidBuilder for ir - no repository rewriting on this network: dl.google.com answered normally,
// so the project's own google() repositories are used as they are.
GRADLE_EOF
fi

# ── the Gradle distribution stays on its own server ─────────────────────────────
# The Gradle distribution itself is NOT mirrored: services.gradle.org is reachable and not
# sanctioned, so every gradle-wrapper.properties keeps its own distributionUrl. Only Google
# Maven (dl.google.com), which is the blocked one, is sent through the mirror.
touch "$GRADLE_USER_DIR/gradle.properties"

# ── long timeouts: a phone on a slow link stalls on the 10s/60s Gradle defaults ───
# The wrapper aborts a distribution download after networkTimeout ms (10s by default), and
# Gradle's HTTP client gives up on a stalled socket after 30s. Both are far too tight here, so
# every value is raised and the retry budget is widened.
write_property() {
  KEY="$1"
  VALUE="$2"
  sed -i "/^${KEY}=/d" "$GRADLE_USER_DIR/gradle.properties"
  printf '%s=%s\n' "$KEY" "$VALUE" >> "$GRADLE_USER_DIR/gradle.properties"
}

write_property 'systemProp.org.gradle.internal.http.connectionTimeout' 120000
write_property 'systemProp.org.gradle.internal.http.socketTimeout' 300000
write_property 'org.gradle.internal.repository.max.tentatives' 10
write_property 'org.gradle.internal.repository.initial.backoff' 2000

# No background build server. Gradle's daemon keeps a 2 GB JVM alive between builds (and between
# app launches) so the next build starts fast; on a phone that is battery, heat and a build that
# silently keeps running after the panel is closed. Gradle still forks one JVM per build.
write_property 'org.gradle.daemon' false
# The file-watching daemon is a separate, long lived process and is pure overhead on FUSE /sdcard.
write_property 'org.gradle.vfs.watch' false

# The wrapper's own distributionUrl stays untouched; only its read timeout is raised.
if [ "${ANDROIDBUILDER_FIX_WRAPPER_TIMEOUT:-1}" != "0" ]; then
  WRAPPER_N=0
  for props in $(find /sdcard "$HOME" -maxdepth 8 -name gradle-wrapper.properties -type f 2>/dev/null); do
    grep -q '^networkTimeout=' "$props" && sed -i 's/^networkTimeout=.*/networkTimeout=120000/' "$props" \
      || printf '\nnetworkTimeout=120000\n' >> "$props"
    WRAPPER_N=$((WRAPPER_N + 1))
  done
  [ "$WRAPPER_N" -gt 0 ] && echo "  raised networkTimeout to 120000 in $WRAPPER_N gradle-wrapper.properties file(s)"
fi

say "done"
echo "  init script: $INIT_SCRIPT"
if [ "$USE_MIRROR" = "1" ]; then
  echo "Google Maven resolves through $MAVEN_MIRROR; the project's own repositories are kept."
else
  echo "Google Maven is used directly, the way the project declares it."
fi
echo "The Gradle distribution keeps coming from services.gradle.org (unchanged)."
echo "Download timeouts raised: HTTP socket 300s, connect 120s, 10 retries."
echo "No daemon and no file-watching daemon: nothing stays resident after the build."
