package com.jsoncode.ghostide;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class JsonToCodePlugin implements GhostPlugin {

  private static volatile PluginContext pluginContext;

  private LongPressController longPressController;
  private EditorHost editorHost;

  public static PluginContext getPluginContext() {
    return pluginContext;
  }

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return Collections.emptyList();
  }

  @Override
  public void activate(PluginContext context) {
    pluginContext = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    editorHost = context.getServices().get(IdeHostServices.EDITOR_HOST);
    longPressController = new LongPressController(androidContext);
    LongPressController controller = longPressController;
    attachCurrentEditor(editorHost);

    Disposable registration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                new JsonToCodePanel(androidContext, context, this),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(registration);
    context.registerDisposable(controller::close);
    context.getLogger().info("JSON to Code activated");
  }

  void attachCurrentEditor(EditorHost host) {
    if (longPressController != null) {
      longPressController.attach(host);
    }
  }

  @Override
  public void deactivate() {
    if (longPressController != null) {
      longPressController.close();
      longPressController = null;
    }
    editorHost = null;
    pluginContext = null;
  }
}
