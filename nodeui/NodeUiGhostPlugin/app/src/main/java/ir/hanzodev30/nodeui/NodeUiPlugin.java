package ir.hanzodev30.nodeui;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class NodeUiPlugin implements GhostPlugin {

  private Disposable panelRegistration;
  private Disposable eventRegistration;
  private NodePanelView view;
  private NpmCheckerService checkerService;

  @Override
  public void activate(PluginContext context) {
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    this.view = new NodePanelView(androidContext, context);
    this.checkerService = new NpmCheckerService(context);
    String owner = context.getDescriptor().getId();

    this.panelRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                new NodePanel(view, androidContext),
                owner,
                0);
    context.registerDisposable(panelRegistration);

    this.eventRegistration =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                event -> {
                  NodePanelView current = view;
                  if (current != null) {
                    current.onFileEvent();
                  }
                  NpmCheckerService checker = checkerService;
                  if (checker != null) {
                    checker.onFileEvent(event);
                  }
                },
                owner,
                0);
    context.registerDisposable(eventRegistration);

    context.getLogger().info("Node Packages ready");
  }

  @Override
  public void deactivate() {
    if (panelRegistration != null) {
      panelRegistration.dispose();
      panelRegistration = null;
    }
    if (eventRegistration != null) {
      eventRegistration.dispose();
      eventRegistration = null;
    }
    if (checkerService != null) {
      checkerService.dispose();
      checkerService = null;
    }
    if (view != null) {
      view.shutdown();
      view = null;
    }
  }
}
