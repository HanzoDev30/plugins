package com.opencode.chat;

import android.content.Context;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * OpenCode Chat — a demo GhostIDE plugin.
 *
 * <p>Registers an {@link EditorPanel} (hosted <em>inside</em> the editor screen, VS Code style,
 * instead of a separate Activity) that talks to the opencode (opencode.ai) coding agent over its
 * HTTP server API ({@code opencode serve}, default http://127.0.0.1:4096). It also demos {@link
 * ir.hanzodev1375.ghostide.ide.ui.api.EditorHost} (attach the open file / insert the reply) and
 * {@link ir.hanzodev1375.ghostide.plugin.api.PluginLogger}.
 */
public final class OpenCodeChatPlugin implements GhostPlugin {

  /** Last activated context; the chat view picks services up from here. */
  private static volatile PluginContext pluginContext;

  /** Convenience accessor used by the chat panel/view. */
  public static PluginContext getPluginContext() {
    return pluginContext;
  }

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of(
        new PluginSetupAction(
            "install-opencode-cli",
            "Install opencode CLI",
            "npm install -g opencode-ai && opencode --version",
            "Installs the opencode coding agent CLI inside the sandbox so you can run "
                + "`opencode serve` (then point this plugin at it)."));
  }

  @Override
  public void activate(PluginContext context) {
    pluginContext = context;

    // The plugin's own scoped Context: required so EditorPanel.createView() can build Views.
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);

    Disposable panel =
        context.getExtensions()
            .register(PluginUiExtensionPoints.EDITOR_PANEL, new OpenCodeChatPanel(androidContext));
    context.registerDisposable(panel);

    context
        .getLogger()
        .info("OpenCode Chat activated. Run `opencode serve`, open a file, then open the Chat panel from the editor toolbar.");
  }

  @Override
  public void deactivate() {
    pluginContext = null;
  }
}
