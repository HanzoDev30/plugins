package ir.hanzodev1375.jadx;

import android.content.Context;

import ir.hanzodev1375.jadx.bridge.JadxEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * Loads the jadx engine once per process.
 *
 * <p>The engine is a separate apk stored in the plugin's assets. It is copied to the code cache
 * (read-only, which Android 14+ requires for dex files) and loaded with {@link EngineClassLoader}.
 * Nothing is added to the host app.
 */
final class EngineHolder {

  private static final String ASSET = "jadx-engine.apk";
  private static final String IMPL = "ir.hanzodev1375.jadx.engine.JadxEngineImpl";

  private final Context pluginContext;
  private JadxEngine engine;

  EngineHolder(Context pluginContext) {
    this.pluginContext = pluginContext;
  }

  synchronized JadxEngine get() throws Exception {
    if (engine != null) {
      return engine;
    }
    File apk = extract();
    ClassLoader parent = JadxEngine.class.getClassLoader();
    EngineClassLoader loader = new EngineClassLoader(apk.getAbsolutePath(), parent);
    Class<?> impl = loader.loadClass(IMPL);
    engine = (JadxEngine) impl.getDeclaredConstructor().newInstance();
    return engine;
  }

  private File extract() throws IOException {
    File base = pluginContext.getCodeCacheDir();
    if (base == null) {
      base = pluginContext.getCacheDir();
    }
    File dir = new File(base, "jadx-engine");
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Cannot create " + dir);
    }

    InputStream in;
    try {
      in = pluginContext.getAssets().open(ASSET);
    } catch (IOException e) {
      throw new IOException(
          "assets/" + ASSET + " is missing from the .gpl. Build it with :plugin:packageGpl.", e);
    }

    File tmp = new File(dir, "engine.tmp");
    if (tmp.exists()) {
      tmp.setWritable(true);
      tmp.delete();
    }
    CRC32 crc = new CRC32();
    try (InputStream source = in;
        OutputStream out = new FileOutputStream(tmp)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = source.read(buffer)) > 0) {
        out.write(buffer, 0, read);
        crc.update(buffer, 0, read);
      }
    }

    // The name carries the checksum, so a rebuilt engine never reuses an old (possibly loaded) file.
    File target = new File(dir, "engine-" + Long.toHexString(crc.getValue()) + ".apk");
    if (target.isFile()) {
      tmp.delete();
    } else if (!tmp.renameTo(target)) {
      throw new IOException("Cannot move " + tmp + " to " + target);
    }
    // Android 14+ refuses to load writable dex files.
    target.setReadOnly();

    File[] old = dir.listFiles();
    if (old != null) {
      for (File file : old) {
        String name = file.getName();
        if (name.startsWith("engine-") && !file.equals(target)) {
          file.setWritable(true);
          file.delete();
        }
      }
    }
    return target;
  }
}
