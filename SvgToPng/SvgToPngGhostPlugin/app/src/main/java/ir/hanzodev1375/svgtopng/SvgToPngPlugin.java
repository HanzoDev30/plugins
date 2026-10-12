package ir.hanzodev1375.svgtopng;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/** Entry class of the SVG to PNG plugin. */
public final class SvgToPngPlugin implements GhostPlugin {

  @Override
  public void activate(PluginContext context) {
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);

    SvgToPngHandler handler = new SvgToPngHandler(context, androidContext);
    Disposable registration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.FILE_CLICK_HANDLER,
                handler,
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(registration);

    context.getLogger().info("SVG to PNG activated");
  }

  @Override
  public void deactivate() {}
}
