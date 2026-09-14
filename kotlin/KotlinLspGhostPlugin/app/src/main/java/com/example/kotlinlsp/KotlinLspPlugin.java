package com.example.kotlinlsp;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class KotlinLspPlugin implements GhostPlugin {

  /**
   * Installs fwcd/kotlin-language-server 1.3.13 (JVM distribution) into /opt/kotlin-lsp and then
   * upgrades the Kotlin compiler it bundles from 2.1.0 to 2.4.10. The upgrade is required because
   * this device's JDK is OpenJDK 25, and the IntelliJ version parser in the old bundled Kotlin
   * 2.1.0 compiler crashes with "IllegalArgumentException: 25.0.4". Kotlin 2.4.10 runs fine on
   * JDK 25 (verified by handshake-testing the server on this device).
   *
   * <p>Downloads come from github.com and Maven Central (mirrored via maven.aliyun.com as a
   * fallback) - both reachable from Iran.
   *
   * <p>{@link #INSTALL_BODY} is the plain, readable installer script. It is written verbatim to
   * /opt/kotlin-lsp/install.sh with a single heredoc, then that file is executed directly - the
   * same as pasting it into a terminal and running {@code bash install.sh} by hand.
   */
  private static final String INSTALL_BODY =
      "#!/usr/bin/env bash\n"
          + "set -e\n"
          + "KLS_DIR=/opt/kotlin-lsp\n"
          + "KLS_VERSION=1.3.13\n"
          + "KOTLIN_VERSION=2.4.10\n"
          + "export DEBIAN_FRONTEND=noninteractive\n"
          + "if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then\n"
          + "  apt-get update >/dev/null && apt-get install -y curl unzip >/dev/null\n"
          + "fi\n"
          + "mkdir -p \"$KLS_DIR\"\n"
          + "if [ -x \"$KLS_DIR/server/bin/kotlin-language-server\" ]; then\n"
          + "  echo 'Kotlin language server already installed; skipping download.'\n"
          + "else\n"
          + "  curl -fL --retry 3 -o /tmp/kls-server.zip \"https://github.com/fwcd/kotlin-language-server/releases/download/${KLS_VERSION}/server.zip\"\n"
          + "  unzip -o /tmp/kls-server.zip -d \"$KLS_DIR\"\n"
          + "  rm -f /tmp/kls-server.zip\n"
          + "fi\n"
          + "LIB_DIR=\"$KLS_DIR/server/lib\"\n"
          + "if ! ls \"$LIB_DIR\"/kotlin-compiler-*.jar >/dev/null 2>&1; then\n"
          + "  echo 'kotlin compiler jars not found under $LIB_DIR' >&2\n"
          + "  exit 1\n"
          + "fi\n"
          + "COMPILER_JAR=$(ls \"$LIB_DIR\"/kotlin-compiler-*.jar | head -n1)\n"
          + "STDLIB_JAR=$(ls \"$LIB_DIR\"/kotlin-stdlib-*.jar | head -n1)\n"
          + "if unzip -p \"$COMPILER_JAR\" META-INF/MANIFEST.MF 2>/dev/null | grep -q \"Implementation-Version: $KOTLIN_VERSION\"; then\n"
          + "  echo \"Kotlin compiler already upgraded to ${KOTLIN_VERSION}.\"\n"
          + "else\n"
          + "  echo 'Upgrading bundled Kotlin compiler to '$KOTLIN_VERSION' (required for OpenJDK 25)...'\n"
          + "  mkdir -p /tmp/kls-patch\n"
          + "  cp \"$COMPILER_JAR\" \"$COMPILER_JAR.bak\"\n"
          + "  cp \"$STDLIB_JAR\" \"$STDLIB_JAR.bak\"\n"
          + "  DL_OK=1\n"
          + "  for u in \\\n"
          + "    \"https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler/${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.jar\" \\\n"
          + "    \"https://maven.aliyun.com/repository/central/org/jetbrains/kotlin/kotlin-compiler/${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.jar\"; do\n"
          + "    echo \"Downloading kotlin-compiler from: $u\"\n"
          + "    if curl -fL --retry 3 -o /tmp/kls-patch/kotlin-compiler.jar \"$u\"; then DL_OK=0; break; fi\n"
          + "  done\n"
          + "  if [ \"$DL_OK\" -ne 0 ]; then echo 'ERROR: failed to download kotlin-compiler jar from all mirrors' >&2; exit 1; fi\n"
          + "  DL_OK=1\n"
          + "  for u in \\\n"
          + "    \"https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/${KOTLIN_VERSION}/kotlin-stdlib-${KOTLIN_VERSION}.jar\" \\\n"
          + "    \"https://maven.aliyun.com/repository/central/org/jetbrains/kotlin/kotlin-stdlib/${KOTLIN_VERSION}/kotlin-stdlib-${KOTLIN_VERSION}.jar\"; do\n"
          + "    echo \"Downloading kotlin-stdlib from: $u\"\n"
          + "    if curl -fL --retry 3 -o /tmp/kls-patch/kotlin-stdlib.jar \"$u\"; then DL_OK=0; break; fi\n"
          + "  done\n"
          + "  if [ \"$DL_OK\" -ne 0 ]; then echo 'ERROR: failed to download kotlin-stdlib jar from all mirrors' >&2; exit 1; fi\n"
          + "  cp /tmp/kls-patch/kotlin-compiler.jar \"$COMPILER_JAR\"\n"
          + "  cp /tmp/kls-patch/kotlin-stdlib.jar \"$STDLIB_JAR\"\n"
          + "  for f in \"$LIB_DIR\"/kotlin-stdlib-jdk*.jar; do\n"
          + "    [ -e \"$f\" ] && cp /tmp/kls-patch/kotlin-stdlib.jar \"$f\"\n"
          + "  done\n"
          + "  rm -rf /tmp/kls-patch\n"
          + "  echo 'Kotlin compiler upgraded to '$KOTLIN_VERSION'.'\n"
          + "fi\n"
          + "cat > /usr/local/bin/kotlin-language-server <<'WRAPPER'\n"
          + "#!/usr/bin/env bash\n"
          + "# Runs the Kotlin language server on this device's installed JDK.\n"
          + "# If `java` resolves through a symlink, derive JAVA_HOME so the Gradle\n"
          + "# launcher script can find it even when JAVA_HOME is not exported.\n"
          + "if [ -z \"$JAVA_HOME\" ]; then\n"
          + "  JAVA_BIN=$(command -v java 2>/dev/null)\n"
          + "  if [ -n \"$JAVA_BIN\" ]; then\n"
          + "    REAL=$(readlink -f \"$JAVA_BIN\" 2>/dev/null || echo \"$JAVA_BIN\")\n"
          + "    CANDIDATE=$(dirname \"$(dirname \"$REAL\")\")\n"
          + "    if [ -x \"$CANDIDATE/bin/java\" ]; then\n"
          + "      export JAVA_HOME=\"$CANDIDATE\"\n"
          + "    fi\n"
          + "  fi\n"
          + "fi\n"
          + "if [ -z \"$JAVA_HOME\" ]; then\n"
          + "  for j in /usr/lib/jvm/*/bin/java; do\n"
          + "    if [ -x \"$j\" ]; then\n"
          + "      export JAVA_HOME=$(dirname \"$(dirname \"$j\")\")\n"
          + "      break\n"
          + "    fi\n"
          + "  done\n"
          + "fi\n"
          + "# Speed up cold start inside proot: skip the C2 JIT tier (we only run for a\n"
          + "# short-lived request/response handshake, so peak throughput doesn't matter\n"
          + "# as much as time-to-first-response) and use the lighter serial GC.\n"
          + "export JAVA_OPTS=\"${JAVA_OPTS:-} -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto\"\n"
          + "exec /opt/kotlin-lsp/server/bin/kotlin-language-server \"$@\"\n"
          + "WRAPPER\n"
          + "chmod +x /usr/local/bin/kotlin-language-server\n"
          + "# Bypass Gradle/Maven auto-detection: this proot environment has neither, so on\n"
          + "# every \"initialize\" the server was falling through to DefaultClassPathResolver\n"
          + "# trying to shell out to Gradle/Maven before giving up - eating well past the\n"
          + "# client's connect timeout. A global classpath script (documented at\n"
          + "# https://github.com/fwcd/kotlin-language-server#editing-a-standalone-file)\n"
          + "# short-circuits that: the server uses it directly instead of probing for a\n"
          + "# build system. This just exposes the bundled stdlib; per-project jars can\n"
          + "# still be added via a project-root kls-classpath script if ever needed.\n"
          + "mkdir -p /root/.config/kotlin-language-server\n"
          + "cat > /root/.config/kotlin-language-server/classpath <<'CLASSPATH_EOF'\n"
          + "#!/bin/bash\n"
          + "printf '%s' \"$(ls /opt/kotlin-lsp/server/lib/kotlin-stdlib-*.jar 2>/dev/null | tr '\\n' ':')\"\n"
          + "CLASSPATH_EOF\n"
          + "chmod +x /root/.config/kotlin-language-server/classpath\n"
          + "echo \"Kotlin language server ${KLS_VERSION} (Kotlin compiler ${KOTLIN_VERSION}) installed.\"\n"
          + "echo 'Running smoke test...'\n"
          + "printf '' | timeout 60 /usr/local/bin/kotlin-language-server >/dev/null 2>&1 && echo 'Smoke test passed: server launches on the installed JDK.' || echo 'WARNING: smoke test failed - check the JDK installation.'\n";

  private static final String INSTALL_SCRIPT =
      "mkdir -p /opt/kotlin-lsp\n"
          + "cat > /opt/kotlin-lsp/install.sh <<'INSTALL_EOF'\n"
          + INSTALL_BODY
          + "INSTALL_EOF\n"
          + "chmod +x /opt/kotlin-lsp/install.sh\n"
          + "/opt/kotlin-lsp/install.sh\n";

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of(
        new PluginSetupAction(
            "install-kotlin-language-server",
            "Install Kotlin language server",
            INSTALL_SCRIPT,
            "Writes the installer to /opt/kotlin-lsp/install.sh with a single heredoc and then "
                + "executes that file. Downloads fwcd/kotlin-language-server 1.3.13 (server.zip) "
                + "from GitHub into /opt/kotlin-lsp and upgrades its bundled Kotlin compiler to "
                + "2.4.10 (from Maven Central / Aliyun mirror). The upgrade is required because "
                + "the bundled compiler 2.1.0 cannot run on this device's OpenJDK 25 "
                + "(IllegalArgumentException: 25.0.4). Finally it writes a kotlin-language-server "
                + "launcher to /usr/local/bin that auto-detects JAVA_HOME."));
  }

  @Override
  public void activate(PluginContext context) {
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    KotlinLspProvider provider = new KotlinLspProvider(launcher);
    Disposable registration =
        context.getExtensions().register(EditorExtensionPoints.LSP_SERVER_PROVIDER, provider);
    context.registerDisposable(registration);
    context
        .getLogger()
        .info(
            provider.isInstalled()
                ? "kotlin-language-server found in rootfs"
                : "kotlin-language-server not installed yet; run the setup action from the Plugin Manager");
  }
}
