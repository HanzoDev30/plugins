package ir.hanzodev1375.jadx;

import ir.hanzodev1375.jadx.bridge.JadxOptions;
import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

/** Plugin settings, persisted in the host's {@link PluginStorage}. */
final class JadxSettings {

  static final String KEY_TAP_DEX = "tap_dex";
  static final String KEY_DEOBF = "deobf";
  static final String KEY_BAD_CODE = "bad_code";
  static final String KEY_FALLBACK = "fallback";

  private final PluginStorage storage;

  /** Cached: {@code FileClickHandler.supports} runs on the main thread for every tap. */
  private volatile boolean tapDex;

  JadxSettings(PluginStorage storage) {
    this.storage = storage;
    this.tapDex = storage.getBoolean(KEY_TAP_DEX, true);
  }

  boolean isTapDex() {
    return tapDex;
  }

  void setTapDex(boolean value) {
    tapDex = value;
    storage.putBoolean(KEY_TAP_DEX, value);
  }

  boolean isDeobf() {
    return storage.getBoolean(KEY_DEOBF, false);
  }

  void setDeobf(boolean value) {
    storage.putBoolean(KEY_DEOBF, value);
  }

  boolean isBadCode() {
    return storage.getBoolean(KEY_BAD_CODE, false);
  }

  void setBadCode(boolean value) {
    storage.putBoolean(KEY_BAD_CODE, value);
  }

  boolean isFallback() {
    return storage.getBoolean(KEY_FALLBACK, false);
  }

  void setFallback(boolean value) {
    storage.putBoolean(KEY_FALLBACK, value);
  }

  JadxOptions toOptions() {
    JadxOptions options = new JadxOptions();
    options.deobfuscation = isDeobf();
    options.showBadCode = isBadCode();
    options.fallbackMode = isFallback();
    return options;
  }
}
