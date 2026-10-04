package ir.hanzodev1375.angular;

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

public final class AngularPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-angular-lsp.sh";
  private static final String SETUP_DIR = "/opt/angular-lsp";

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
            + SETUP_DIR
            + "\ncat > "
            + SETUP_DIR
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\nchmod +x "
            + SETUP_DIR
            + "/install.sh\n"
            + SETUP_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-angular-language-server",
            "Install Angular language server (@angular/language-server)",
            command,
            "Installs Node.js, @angular/language-server, @angular/language-service and typescript "
                + "inside the proot rootfs. Provides completion, diagnostics, hover and "
                + "go-to-definition for Angular templates (.html and inline templates in .ts)."));
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
                new AngularLspProvider(launcher, context.getLogger()),
                context.getDescriptor().getId(),
                AngularLspProvider.PRIORITY);
    context.registerDisposable(lsp);

    context.getLogger().info("Angular language support registered (priority " + AngularLspProvider.PRIORITY + ")");
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
