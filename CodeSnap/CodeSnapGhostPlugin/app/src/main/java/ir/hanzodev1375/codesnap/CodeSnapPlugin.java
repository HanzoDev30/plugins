package ir.hanzodev1375.codesnap;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class CodeSnapPlugin implements GhostPlugin {

  private CodeSnapPanel panel;

  @Override
  public void activate(PluginContext context) {
    String owner = context.getDescriptor().getId();
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    panel = new CodeSnapPanel(androidContext, context);

    Disposable panelReg =
        context
            .getExtensions()
            .register(PluginUiExtensionPoints.EDITOR_PANEL, panel, owner, 0);
    context.registerDisposable(panelReg);

    Disposable eventReg =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                event -> {
                  if ("SAVED".equals(event.type().name())) {
                    panel.onFileSaved(event.path());
                  }
                },
                owner,
                0);
    context.registerDisposable(eventReg);

    context.getLogger().info("Code Snap activated");
  }

  @Override
  public void deactivate() {
    if (panel != null) {
      panel.destroy();
      panel = null;
    }
  }
}
