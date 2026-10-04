package ir.hanzodev1375.bootstrap;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class BootstrapPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-bootstrap-lsp.sh";
  private static final String SERVER_ASSET = "bootstrap-lsp.js";
  private static final String SETUP_DIR = "/opt/bootstrap-lsp";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String install = readAsset(context, INSTALL_SCRIPT_ASSET);
    String server = readAsset(context, SERVER_ASSET);
    if (install.isBlank() || server.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p " + SETUP_DIR
            + "\ncat > " + SETUP_DIR + "/server.js <<'BOOTSTRAP_JS_EOF'\n"
            + server
            + "\nBOOTSTRAP_JS_EOF\ncat > " + SETUP_DIR + "/install.sh <<'INSTALL_EOF'\n"
            + install
            + "\nINSTALL_EOF\nchmod +x " + SETUP_DIR + "/install.sh\n"
            + SETUP_DIR + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-bootstrap-language-server",
            "Install Bootstrap + Emmet + HTML language servers",
            command,
            "Installs Node.js, the bootstrap, vscode-langservers-extracted and "
                + "@olrtg/emmet-language-server npm packages inside the proot rootfs and writes the "
                + "proxy server. Adds Bootstrap class completion and hover to class=\"...\" in .html "
                + "files, next to the normal HTML and Emmet features."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    Disposable lsp =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                new BootstrapLspProvider(launcher, context.getLogger()),
                context.getDescriptor().getId(),
                BootstrapLspProvider.PRIORITY);
    context.registerDisposable(lsp);

    context.getLogger().info("Bootstrap language support registered (priority " + BootstrapLspProvider.PRIORITY + ")");
  }

  private static String readAsset(PluginContext context, String name) {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}
