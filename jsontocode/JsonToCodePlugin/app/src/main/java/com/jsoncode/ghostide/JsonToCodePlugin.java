package com.jsoncode.ghostide;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class JsonToCodePlugin implements GhostPlugin, LongPressController.Listener {

  private static volatile PluginContext pluginContext;

  private LongPressController longPressController;
  private EditorHost editorHost;

  private volatile JsonToCodeView activeView;
  private volatile String pendingJson;

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

    longPressController = new LongPressController(this::resolveHost, this);
    LongPressController controller = longPressController;
    // Started immediately, not when the panel opens, so a long press on a fresh editor is seen.
    controller.start();

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

  private EditorHost resolveHost() {
    PluginContext context = pluginContext;
    if (context == null) {
      return null;
    }
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host != null) {
      editorHost = host;
    }
    return host != null ? host : editorHost;
  }

  @Override
  public void onJsonCaptured(String json) {
    pendingJson = json;
    JsonToCodeView view = activeView;
    if (view == null) {
      notifyUser("JSON captured. Open the JSON to Code panel to generate.");
      return;
    }
    view.acceptJson(json);
    notifyUser("JSON block selected.");
  }

  @Override
  public void onLongPressRejected(String reason) {
    notifyUser(reason);
  }

  /** Returns true when a pending long press capture was delivered to the view. */
  boolean attachView(JsonToCodeView view) {
    activeView = view;
    String json = pendingJson;
    if (json == null) {
      return false;
    }
    pendingJson = null;
    view.acceptJson(json);
    return true;
  }

  void detachView(JsonToCodeView view) {
    if (activeView == view) {
      activeView = null;
    }
  }

  private void notifyUser(String message) {
    PluginContext context = pluginContext;
    if (context == null) {
      return;
    }
    UiFeedbackHost feedback = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message, false);
    }
  }

  @Override
  public void deactivate() {
    if (longPressController != null) {
      longPressController.close();
      longPressController = null;
    }
    activeView = null;
    pendingJson = null;
    editorHost = null;
    pluginContext = null;
  }
}
