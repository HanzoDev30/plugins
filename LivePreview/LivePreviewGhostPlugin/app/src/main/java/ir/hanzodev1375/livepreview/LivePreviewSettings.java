package ir.hanzodev1375.livepreview;

import android.webkit.WebSettings;

import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

/**
 * WebView preferences exposed in the IDE settings screen. Values live in the plugin's
 * {@link PluginStorage} so they survive restarts; {@link #apply(WebSettings)} pushes the current
 * values into a live WebView. Keys are the same ids the settings contributor uses.
 */
public final class LivePreviewSettings {

  public static final String KEY_JAVASCRIPT = "javascript";
  public static final String KEY_DOM_STORAGE = "dom_storage";
  public static final String KEY_NO_CACHE = "no_cache";
  public static final String KEY_WIDE_VIEWPORT = "wide_viewport";
  public static final String KEY_ZOOM = "zoom";
  public static final String KEY_ZOOM_BUTTONS = "zoom_buttons";
  public static final String KEY_AUTOPLAY = "autoplay";

  private final PluginStorage storage;

  public LivePreviewSettings(PluginStorage storage) {
    this.storage = storage;
  }

  public boolean isJavaScriptEnabled() {
    return storage.getBoolean(KEY_JAVASCRIPT, true);
  }

  public void setJavaScriptEnabled(boolean value) {
    storage.putBoolean(KEY_JAVASCRIPT, value);
  }

  public boolean isDomStorageEnabled() {
    return storage.getBoolean(KEY_DOM_STORAGE, true);
  }

  public void setDomStorageEnabled(boolean value) {
    storage.putBoolean(KEY_DOM_STORAGE, value);
  }

  public boolean isNoCache() {
    return storage.getBoolean(KEY_NO_CACHE, true);
  }

  public void setNoCache(boolean value) {
    storage.putBoolean(KEY_NO_CACHE, value);
  }

  public boolean isWideViewport() {
    return storage.getBoolean(KEY_WIDE_VIEWPORT, true);
  }

  public void setWideViewport(boolean value) {
    storage.putBoolean(KEY_WIDE_VIEWPORT, value);
  }

  public boolean isZoomEnabled() {
    return storage.getBoolean(KEY_ZOOM, true);
  }

  public void setZoomEnabled(boolean value) {
    storage.putBoolean(KEY_ZOOM, value);
  }

  public boolean isZoomButtons() {
    return storage.getBoolean(KEY_ZOOM_BUTTONS, false);
  }

  public void setZoomButtons(boolean value) {
    storage.putBoolean(KEY_ZOOM_BUTTONS, value);
  }

  public boolean isAutoplay() {
    return storage.getBoolean(KEY_AUTOPLAY, true);
  }

  public void setAutoplay(boolean value) {
    storage.putBoolean(KEY_AUTOPLAY, value);
  }

  /** Applies every preference to a WebView's settings. Must run on the UI thread. */
  public void apply(WebSettings s) {
    s.setJavaScriptEnabled(isJavaScriptEnabled());
    s.setDomStorageEnabled(isDomStorageEnabled());
    s.setCacheMode(isNoCache() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
    s.setUseWideViewPort(isWideViewport());
    s.setBuiltInZoomControls(isZoomEnabled());
    s.setDisplayZoomControls(isZoomEnabled() && isZoomButtons());
    s.setMediaPlaybackRequiresUserGesture(!isAutoplay());
  }
}
