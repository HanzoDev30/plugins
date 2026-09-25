#!/usr/bin/env bash
# AndroidBuilder for ir - point Gradle at the Iranian mirror maven.myket.ir.
#
# Google Maven (dl.google.com / maven.google.com) is unreachable or extremely slow from Iran, and
# it is where AGP, the AndroidX artifacts and the build-tools metadata come from. Instead of adding
# repositories - which would break projects that set RepositoriesMode.FAIL_ON_PROJECT_REPOS - this
# rewrites the *existing* google() repositories to the mirror and puts the mirror first for plugin
# and buildscript resolution.
set -e

MAVEN_MIRROR="${ANDROIDBUILDER_MAVEN_MIRROR:-https://maven.myket.ir}"
GRADLE_USER_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

mkdir -p "$GRADLE_USER_DIR"
INIT_SCRIPT="$GRADLE_USER_DIR/init.gradle"

say "writing $INIT_SCRIPT (mirror: $MAVEN_MIRROR)"
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
echo "Google Maven now resolves through $MAVEN_MIRROR; the project's own repositories are kept."
echo "The Gradle distribution keeps coming from services.gradle.org (unchanged)."
echo "Download timeouts raised: HTTP socket 300s, connect 120s, 10 retries."
