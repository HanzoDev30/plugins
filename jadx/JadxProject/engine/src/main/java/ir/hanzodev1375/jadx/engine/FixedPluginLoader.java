package ir.hanzodev1375.jadx.engine;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.loader.JadxPluginLoader;
import jadx.plugins.input.dex.DexInputPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * jadx normally finds its plugins with {@link java.util.ServiceLoader}, which needs
 * META-INF/services. We register the only plugin we need (dex input) explicitly instead.
 */
final class FixedPluginLoader implements JadxPluginLoader {

  @Override
  public List<JadxPlugin> load() {
    List<JadxPlugin> list = new ArrayList<>();
    list.add(new DexInputPlugin());
    return list;
  }

  @Override
  public void close() {
    // nothing to close
  }
}
