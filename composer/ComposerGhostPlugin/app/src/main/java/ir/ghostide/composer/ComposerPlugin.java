package ir.ghostide.composer;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * Composer — a panel next to the editor that installs, updates and removes PHP packages without
 * ever opening a terminal.
 *
 * <p>Everything runs inside the app's proot Debian through {@link ProotShell}, the same rootfs the
 * built-in language support uses. The panel is reachable from the plugin popup (and the editor's
 * panel list) because it is registered at {@link PluginUiExtensionPoints#EDITOR_PANEL}.
 */
public final class ComposerPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-composer.sh";
  private static final String INSTALL_DIR = "/opt/composer";

  private PluginContext context;
  private Disposable panelRegistration;
  private ComposerPanelView view;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (body.isEmpty()) {
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
            "install-composer",
            "Install PHP and Composer",
            command,
            "Installs the PHP CLI plus its common extensions and the Composer dependency manager "
                + "inside the proot rootfs. Creates /usr/local/bin/composer so the panel and the "
                + "terminal can both run it."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    this.view = new ComposerPanelView(androidContext, context);
    this.panelRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                new ComposerPanel(view),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(panelRegistration);
    context.getLogger().info("Composer ready - open the plugin popup for the panel");
  }

  @Override
  public void deactivate() {
    if (panelRegistration != null) {
      panelRegistration.dispose();
      panelRegistration = null;
    }
    view = null;
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
