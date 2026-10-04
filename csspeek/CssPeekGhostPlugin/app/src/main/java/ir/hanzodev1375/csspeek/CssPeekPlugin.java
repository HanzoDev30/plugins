package ir.hanzodev1375.csspeek;

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

public final class CssPeekPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-css-peek-lsp.sh";
  private static final String SERVER_ASSET = "css-peek-lsp.js";
  private static final String CORE_ASSET = "css-peek-core.js";
  private static final String SETUP_DIR = "/opt/csspeek-lsp";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String install = readAsset(context, INSTALL_SCRIPT_ASSET);
    String server = readAsset(context, SERVER_ASSET);
    String core = readAsset(context, CORE_ASSET);
    if (install.isBlank() || server.isBlank() || core.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p " + SETUP_DIR
            + "\ncat > " + SETUP_DIR + "/server.js <<'CSSPEEK_SERVER_EOF'\n"
            + server
            + "\nCSSPEEK_SERVER_EOF\ncat > " + SETUP_DIR + "/css-peek-core.js <<'CSSPEEK_CORE_EOF'\n"
            + core
            + "\nCSSPEEK_CORE_EOF\ncat > " + SETUP_DIR + "/install.sh <<'INSTALL_EOF'\n"
            + install
            + "\nINSTALL_EOF\nchmod +x " + SETUP_DIR + "/install.sh\n"
            + SETUP_DIR + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-css-peek-language-server",
            "Install CSS Peek (go to definition for class/id in HTML)",
            command,
            "Writes the CSS Peek proxy server into the proot rootfs and wires it to the HTML and "
                + "Emmet language servers that are already installed (installing them only if "
                + "missing). Needs Node.js 18+."));
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
                new CssPeekLspProvider(launcher, context.getLogger()),
                context.getDescriptor().getId(),
                CssPeekLspProvider.PRIORITY);
    context.registerDisposable(lsp);

    context.getLogger().info("CSS Peek registered (priority " + CssPeekLspProvider.PRIORITY + ")");
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
