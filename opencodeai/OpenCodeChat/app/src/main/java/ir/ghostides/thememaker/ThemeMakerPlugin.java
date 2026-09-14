package ir.ghostides.thememaker;

import android.content.Context;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

import ir.ghostides.thememaker.ui.ImagePicker;

/**
 * Ghost Theme Maker — turn any image into a GhostIDE theme.
 *
 * <p>Registers an {@link ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel} that lets you pick an
 * image from the gallery, extracts a colour palette from it and generates a complete GhostIDE
 * theme (activity / editor / widget) that can be applied to the running IDE or saved into the open
 * project as a JSON theme file.
 */
public final class ThemeMakerPlugin implements GhostPlugin {

  /** Last activated context; the panel picks the editor host / services up from here. */
  private static volatile PluginContext pluginContext;

  public static PluginContext getPluginContext() {
    return pluginContext;
  }

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of();
  }

  @Override
  public void activate(PluginContext context) {
    pluginContext = context;

    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ImagePicker.trackActivities(androidContext);

    Disposable panel =
        context.getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                new ThemeMakerPanel(androidContext));
    context.registerDisposable(panel);

    context
        .getLogger()
        .info("Theme Maker activated. Open the panel from the editor toolbar, pick an image and apply the generated theme.");
  }

  @Override
  public void deactivate() {
    pluginContext = null;
  }
}
