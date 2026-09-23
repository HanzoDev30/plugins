package ir.ghostide.rubylsp;

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
 * Installs Ruby and the Solargraph language server inside the proot rootfs and
 * routes .rb files to it via the LSP protocol over stdio.
 */
public final class RubyLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-ruby-lsp.sh";
  private static final String INSTALL_DIR = "/opt/ruby-lsp";

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
            "install-ruby-language-server",
            "Install Ruby language server (solargraph)",
            command,
            "Installs Ruby, solargraph and rufo inside the proot rootfs. "
                + "Runs the server through solargraph stdio for .rb files. "
                + "Provides completion, diagnostics, hover, go-to-definition, "
                + "rename and formatting."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    RubyLspProvider provider = new RubyLspProvider(launcher);
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
                ? "solargraph found in rootfs"
                : "solargraph not installed yet; run the setup action from the Plugin Manager");
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