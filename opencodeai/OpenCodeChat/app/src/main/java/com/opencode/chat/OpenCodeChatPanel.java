package com.opencode.chat;

import android.content.Context;
import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;

/**
 * One {@link EditorPanel} contributed by this plugin, registered at {@code EDITOR_PANEL}. The host
 * (the editor screen) slides the chat into a side sheet inside the running editor, so you never
 * leave your file — unlike the old {@code PluginScreen} approach, which opened a separate Activity.
 */
public final class OpenCodeChatPanel implements EditorPanel {

  private final Context pluginContext;

  public OpenCodeChatPanel(Context pluginContext) {
    this.pluginContext = pluginContext;
  }

  @Override
  public String getId() {
    return "com.opencode.chat.panel";
  }

  @Override
  public String getTitle() {
    return "OpenCode Chat";
  }

  @Override
  public View createView() {
    return new OpenCodeChatView(pluginContext).getRoot();
  }
}
