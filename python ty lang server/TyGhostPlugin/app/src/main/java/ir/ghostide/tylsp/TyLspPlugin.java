package ir.ghostide.tylsp;

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

/**
 * Installs and runs ty, the Rust based Python type checker from Astral, inside the proot rootfs.
 * Provides Python type diagnostics, hover and completion via the standard LSP protocol.
 */
public final class TyLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-ty-lsp.sh";
  private static final String INSTALL_DIR = "/opt/ty-lsp";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (body.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p "
            + INSTALL_DIR
            + "\n"
            + "cat > "
            + INSTALL_DIR
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\n"
            + "chmod +x "
            + INSTALL_DIR
            + "/install.sh\n"
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-ty-language-server",
            "Install ty language server (astral-sh/ty)",
            command,
            "Installs the ty Python type checker (prebuilt Rust binary) inside the proot "
                + "rootfs. Creates the /usr/local/bin/ty-language-server wrapper which runs "
                + "'ty server'. Provides fast diagnostics, hover, completion and inlay hints "
                + "for .py files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    TyLspProvider provider = new TyLspProvider(launcher);
    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(registration);
    context
        .getLogger()
        .info(
            provider.isInstalled()
                ? "ty-language-server wrapper found in rootfs"
                : "ty not installed yet; run the setup action from the Plugin Manager");
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
