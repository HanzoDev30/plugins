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
def irMirror = '__IR_MIRROR__'
def irHosts = ['dl.google.com', 'maven.google.com', 'plugins.gradle.org', 'jcenter.bintray.com'] as Set
def repoType = org.gradle.api.artifacts.repositories.MavenArtifactRepository

// Rewrites the repositories a build already declares; adding repositories instead would fail
// every project that uses RepositoriesMode.FAIL_ON_PROJECT_REPOS.
def remapGoogle = { repos ->
    repos.all { repo ->
        if (repo instanceof repoType) {
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
    def mirror = repos.maven { it.name = 'irMyketMirror'; it.url = irMirror }
    repos.remove(mirror)
    repos.addFirst(mirror)
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

# ── optional: mirror for the Gradle wrapper distribution itself ──────────────────
if [ -n "${GRADLE_DISTRIBUTION_MIRROR:-}" ]; then
  say "rewriting gradle-wrapper.properties to $GRADLE_DISTRIBUTION_MIRROR"
  FOUND=0
  for props in $(find /sdcard "$HOME" -maxdepth 6 -name gradle-wrapper.properties -type f 2>/dev/null); do
    sed -i "s#^distributionUrl=.*#distributionUrl=$GRADLE_DISTRIBUTION_MIRROR#" "$props"
    echo "  updated $props"
    FOUND=1
  done
  [ "$FOUND" = 1 ] || echo "  no gradle-wrapper.properties found (searched /sdcard and \$HOME, depth 6)"
else
  echo
  echo "Tip: services.gradle.org is slow from Iran. Export GRADLE_DISTRIBUTION_MIRROR=<url>"
  echo "     (a full gradle-<version>-bin.zip url) and run this action again."
fi

touch "$GRADLE_USER_DIR/gradle.properties"

say "done"
echo "  init script: $INIT_SCRIPT"
echo "Google Maven now resolves through $MAVEN_MIRROR; the project's own repositories are kept."
