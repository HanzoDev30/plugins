package com.jsoncode.ghostide;

import android.content.Context;
import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class JsonToCodePanel implements EditorPanel {

  private final Context pluginContext;
  private final PluginContext pluginContextApi;
  private final JsonToCodePlugin owner;

  public JsonToCodePanel(Context pluginContext, PluginContext pluginContextApi, JsonToCodePlugin owner) {
    this.pluginContext = pluginContext;
    this.pluginContextApi = pluginContextApi;
    this.owner = owner;
    setState(PluginStateMod.BOTTOMSHEETDIALOG);
  }

  @Override
  public String getId() {
    return "com.jsoncode.ghostide.panel";
  }

  @Override
  public String getTitle() {
    return "JSON to Code";
  }

  @Override
  public View createView() {
    EditorHost host = pluginContextApi.getServices().get(IdeHostServices.EDITOR_HOST);
    owner.attachCurrentEditor(host);
    return new JsonToCodeView(pluginContext, host).getRoot();
  }
}
