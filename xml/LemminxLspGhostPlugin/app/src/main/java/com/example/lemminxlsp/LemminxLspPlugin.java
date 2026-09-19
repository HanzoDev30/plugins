package com.example.lemminxlsp;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class LemminxLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT =
      "set -e\n"
          + "DEST_DIR=\"$HOME/.local/share/ghostide/lemminx\"\n"
          + "mkdir -p \"$DEST_DIR\"\n"
          + "if ! command -v curl >/dev/null 2>&1; then\n"
          + "  apt-get update && apt-get install -y curl\n"
          + "fi\n"
          + "BASE_URL=\"https://download.eclipse.org/lemminx/releases/\"\n"
          + "VERSION=$(curl -fsSL \"$BASE_URL\" | grep -oE '[0-9]+\\.[0-9]+\\.[0-9]+' | sort -V | uniq | tail -n1)\n"
          + "if [ -z \"$VERSION\" ]; then\n"
          + "  echo \"Could not determine the latest Lemminx version\" >&2\n"
          + "  exit 1\n"
          + "fi\n"
          + "JAR=\"$DEST_DIR/org.eclipse.lemminx-uber.jar\"\n"
          + "curl -fsSL -o \"$JAR\" \"${BASE_URL}${VERSION}/org.eclipse.lemminx-uber.jar\"\n"
          + "cat > /usr/bin/lemminx <<WRAPPER\n"
          + "#!/usr/bin/env bash\n"
          + "exec java -jar \"$JAR\" \"\\$@\"\n"
          + "WRAPPER\n"
          + "chmod +x /usr/bin/lemminx\n"
          + "echo \"Installed Lemminx $VERSION -> /usr/bin/lemminx (jar: $JAR)\"\n";

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of(
        new PluginSetupAction(
            "install-lemminx",
            "Install Lemminx (XML language server)",
            INSTALL_SCRIPT,
            "Downloads the official Eclipse LemMinX uber jar (there is no lemminx "
                + "apt package) and writes a 'lemminx' launcher script directly "
                + "to /usr/bin/lemminx (the exact path LemminxLspProvider execs) "
                + "that runs it with 'java -jar'."));
  }

  @Override
  public void activate(PluginContext context) {
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    LemminxLspProvider provider = new LemminxLspProvider(launcher);
    Disposable registration =
        context.getExtensions().register(EditorExtensionPoints.LSP_SERVER_PROVIDER, provider);
    context.registerDisposable(registration);
    context
        .getLogger()
        .info(
            provider.isInstalled()
                ? "lemminx found in rootfs"
                : "lemminx not installed yet; run the setup action from the Plugin Manager");
  }
}
