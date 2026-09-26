package ir.ghostide.pipui;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Pip Installer — a panel next to the editor that installs Python packages from PyPI without ever
 * opening a terminal.
 *
 * <p>Everything runs inside the app's proot Debian through {@link ProotShell}, the same rootfs the
 * built-in language support uses. The panel is reachable from the plugin popup (and the editor's
 * panel list) because it is registered at {@link PluginUiExtensionPoints#EDITOR_PANEL}.
 */
public final class PipUiPlugin implements GhostPlugin {

  private Disposable panelRegistration;
  private PipPanelView view;

  @Override
  public void activate(PluginContext context) {
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    this.view = new PipPanelView(androidContext, context);
    this.panelRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                new PipPanel(view),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(panelRegistration);
    context.getLogger().info("Pip Installer ready - open the plugin popup for the panel");
  }

  @Override
  public void deactivate() {
    if (panelRegistration != null) {
      panelRegistration.dispose();
      panelRegistration = null;
    }
    view = null;
  }
}
