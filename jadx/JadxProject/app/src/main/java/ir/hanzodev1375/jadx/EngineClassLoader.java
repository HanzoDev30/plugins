package ir.hanzodev1375.jadx;

import dalvik.system.DexClassLoader;

import java.net.URL;

/**
 * Class loader of the jadx engine (the dex inside assets/jadx-engine.apk).
 *
 * <p>Two differences from a plain {@link DexClassLoader}:
 *
 * <ul>
 *   <li><b>Child first.</b> jadx needs its own guava / gson / protobuf / slf4j versions; the host app
 *       may ship different ones and parent-first loading would pick the host's. The bridge contract
 *       and the platform stay parent-first so both sides see the same {@code JadxEngine} class.
 *   <li><b>Own resources first.</b> jadx-core reads {@code /clst/core.jcst}, {@code
 *       /android/res-map.txt} ... through {@code Class.getResourceAsStream}; those files live in the
 *       engine apk.
 * </ul>
 */
final class EngineClassLoader extends DexClassLoader {

  private static final String[] SHARED = {
    "ir.hanzodev1375.jadx.bridge.",
    "java.",
    "javax.",
    "android.",
    "dalvik.",
    "libcore.",
    "sun.",
    "org.xml.",
    "org.w3c.",
    "org.json.",
    "org.apache.harmony.",
    "org.apache.http.",
    "junit."
  };

  EngineClassLoader(String dexPath, ClassLoader parent) {
    super(dexPath, null, null, parent);
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> loaded = findLoadedClass(name);
    if (loaded == null) {
      if (isShared(name)) {
        return super.loadClass(name, resolve);
      }
      try {
        loaded = findClass(name);
      } catch (ClassNotFoundException notInEngine) {
        return super.loadClass(name, resolve);
      }
    }
    if (resolve) {
      resolveClass(loaded);
    }
    return loaded;
  }

  @Override
  public URL getResource(String name) {
    URL own = findResource(name);
    return own != null ? own : super.getResource(name);
  }

  private static boolean isShared(String name) {
    for (String prefix : SHARED) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
