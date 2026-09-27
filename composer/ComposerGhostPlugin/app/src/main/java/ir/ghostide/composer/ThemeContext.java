package ir.ghostide.composer;

import android.content.Context;
import android.os.Build;
import android.view.ContextThemeWrapper;

/**
 * Builds the {@link Context} every view in this plugin is created with.
 *
 * <p>The context the host hands over is the plugin one: it resolves the bundle's own resources but
 * carries no theme, so views built from it directly ignore light/dark. A plugin must not pull in a
 * resource-owning library for this, because every extra resource in the bundle is loaded through
 * {@code AssetManager.addAssetPath} and collides with the host's own table, so the theme comes from
 * the platform: the framework DayNight theme, wrapped around the plugin context so the bundle's own
 * strings keep resolving.
 */
final class ThemeContext {

  private ThemeContext() {}

  static Context themed(Context pluginContext) {
    int theme =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? android.R.style.Theme_DeviceDefault_DayNight
            : android.R.style.Theme_DeviceDefault;
    return new ContextThemeWrapper(pluginContext, theme);
  }
}
