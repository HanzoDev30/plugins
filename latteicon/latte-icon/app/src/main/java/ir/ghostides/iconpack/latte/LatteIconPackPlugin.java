package ir.ghostides.iconpack.latte;

import android.content.Context;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.JsonFileIconContributor;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * Latte Icon — Catppuccin Latte file icons for GhostIDE.
 *
 * <p>Loads {@code assets/myicons.json} (same schema as the host's {@code data/file_icons.json}) and
 * registers it as a {@link JsonFileIconContributor}. Names that exist under {@code assets/myicons/}
 * are served from this plugin; anything else falls back to the built-in vscode icon set.
 */
public final class LatteIconPackPlugin implements GhostPlugin {

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of();
  }

  @Override
  public void activate(PluginContext context) {
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);

    Disposable icons =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.FILE_ICON_CONTRIBUTOR,
                new JsonFileIconContributor(androidContext, "myicons.json"));
    context.registerDisposable(icons);

    context.getLogger().info("Latte Icon activated.");
  }

  @Override
  public void deactivate() {}
}
