package ir.hanzodev1375.jadx;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIconState;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIcons;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.CoreExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.CoreServices;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.ExtensionPoint;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;
import ir.theme.M3Theme;

import android.content.Context;

/**
 * Entry class of the JADX plugin.
 *
 * <ul>
 *   <li>File manager toolbar icon: scans the current folder for .dex files and lists them.
 *   <li>Tap on a .dex file: opens its class browser directly (can be turned off in settings).
 * </ul>
 */
public final class JadxDecompilerPlugin implements GhostPlugin {

  private JadxController controller;

  @Override
  public void activate(PluginContext context) {
    String owner = context.getDescriptor().getId();
    Context android = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    PluginStorage storage = context.getServices().require(CoreServices.PLUGIN_STORAGE);

    JadxSettings settings = new JadxSettings(storage);
    EngineHolder engines = new EngineHolder(android);
    controller = new JadxController(context, android, settings, engines);

    register(
        context,
        owner,
        PluginUiExtensionPoints.FILE_CLICK_HANDLER,
        new DexClickHandler(settings, controller));

    register(
        context, owner, CoreExtensionPoints.PLUGIN_SETTINGS, new JadxSettingsContributor(settings));

    Integer tint = M3Theme.onSurface();
    Disposable icon =
        PluginIcons.setPluginShowState(
            context,
            PluginIconState.FileES,
            L.t("Decompile DEX", "دیکامپایل DEX"),
            controller::onToolbarClick,
            new CodeIconDrawable(tint != null ? tint : 0xFFB0B0B0));
    context.registerDisposable(icon);

    context.getLogger().info("JADX Decompiler activated");
  }

  @Override
  public void deactivate() {
    if (controller != null) {
      controller.shutdown();
      controller = null;
    }
  }

  private static <T> void register(
      PluginContext context, String owner, ExtensionPoint<T> point, T extension) {
    context.registerDisposable(context.getExtensions().register(point, extension, owner, 0));
  }
}
