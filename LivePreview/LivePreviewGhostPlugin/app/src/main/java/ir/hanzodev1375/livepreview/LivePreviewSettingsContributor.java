package ir.hanzodev1375.livepreview;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import ir.hanzodev1375.ghostide.plugin.api.PluginSetting;
import ir.hanzodev1375.ghostide.plugin.api.PluginSettingsContributor;

/**
 * Shows the WebView preferences inside the IDE settings screen. Titles and descriptions come from
 * the plugin's own string resources, so they localize with the rest of the .gpl.
 */
public final class LivePreviewSettingsContributor implements PluginSettingsContributor {

  private final Context androidContext;
  private final LivePreviewSettings settings;
  private final LivePreviewPanel panel;

  public LivePreviewSettingsContributor(
      Context androidContext, LivePreviewSettings settings, LivePreviewPanel panel) {
    this.androidContext = androidContext;
    this.settings = settings;
    this.panel = panel;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.livepreview.settings";
  }

  @Override
  public String getDisplayName() {
    return str(R.string.prefs_group_name);
  }

  @Override
  public List<PluginSetting> getSettings() {
    List<PluginSetting> rows = new ArrayList<>();

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_JAVASCRIPT,
            str(R.string.setting_javascript),
            str(R.string.setting_javascript_desc),
            settings.isJavaScriptEnabled(),
            value -> {
              settings.setJavaScriptEnabled(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_DOM_STORAGE,
            str(R.string.setting_dom_storage),
            str(R.string.setting_dom_storage_desc),
            settings.isDomStorageEnabled(),
            value -> {
              settings.setDomStorageEnabled(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_NO_CACHE,
            str(R.string.setting_no_cache),
            str(R.string.setting_no_cache_desc),
            settings.isNoCache(),
            value -> {
              settings.setNoCache(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_WIDE_VIEWPORT,
            str(R.string.setting_wide_viewport),
            str(R.string.setting_wide_viewport_desc),
            settings.isWideViewport(),
            value -> {
              settings.setWideViewport(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_ZOOM,
            str(R.string.setting_zoom),
            str(R.string.setting_zoom_desc),
            settings.isZoomEnabled(),
            value -> {
              settings.setZoomEnabled(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_ZOOM_BUTTONS,
            str(R.string.setting_zoom_buttons),
            str(R.string.setting_zoom_buttons_desc),
            settings.isZoomButtons(),
            value -> {
              settings.setZoomButtons(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.toggle(
            LivePreviewSettings.KEY_AUTOPLAY,
            str(R.string.setting_autoplay),
            str(R.string.setting_autoplay_desc),
            settings.isAutoplay(),
            value -> {
              settings.setAutoplay(value);
              panel.applyWebSettings();
            }));

    rows.add(
        PluginSetting.action(
            "clear_cache",
            str(R.string.setting_clear_cache),
            str(R.string.setting_clear_cache_desc),
            panel::clearCache));

    return rows;
  }

  private String str(int resId) {
    return androidContext.getString(resId);
  }
}
