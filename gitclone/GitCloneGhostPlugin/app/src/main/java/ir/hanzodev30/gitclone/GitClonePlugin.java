package ir.hanzodev30.gitclone;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class GitClonePlugin implements GhostPlugin {

  private Disposable panelRegistration;
  private GitClonePanelView view;

  @Override
  public void activate(PluginContext context) {
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    this.view = new GitClonePanelView(androidContext, context);
    String owner = context.getDescriptor().getId();

    this.panelRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.FILE_PANEL,
                new GitClonePanel(view, androidContext),
                owner,
                0);
    context.registerDisposable(panelRegistration);

    context.getLogger().info("Git Clone ready - open the plugin popup for the panel");
  }

  @Override
  public void deactivate() {
    if (panelRegistration != null) {
      panelRegistration.dispose();
      panelRegistration = null;
    }
    if (view != null) {
      view.shutdown();
      view = null;
    }
  }
}
