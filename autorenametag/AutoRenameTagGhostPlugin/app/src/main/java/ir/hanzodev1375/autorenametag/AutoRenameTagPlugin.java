package ir.hanzodev1375.autorenametag;

import android.content.Context;

import androidx.annotation.NonNull;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.CoreServices;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

public final class AutoRenameTagPlugin implements GhostPlugin {

  @NonNull private AutoRenameTagEngine engine;

  @Override
  public void activate(@NonNull PluginContext context) {
    PluginStorage storage = context.getServices().require(CoreServices.PLUGIN_STORAGE);
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    AutoRenameTagSettings settings = new AutoRenameTagSettings(storage);
    engine = new AutoRenameTagEngine(settings, context.getLogger());
    context.registerDisposable(engine::stop);
    context.registerDisposable(
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.PLUGIN_SCREEN,
                new AutoRenameTagSettingsScreen(settings, androidContext),
                context.getDescriptor().getId(),
                0));
    engine.start(context);
  }

  @Override
  public void deactivate() {
    if (engine != null) {
      engine.stop();
      engine = null;
    }
  }
}
