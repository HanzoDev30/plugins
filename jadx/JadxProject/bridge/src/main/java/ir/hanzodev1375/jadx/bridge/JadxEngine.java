package ir.hanzodev1375.jadx.bridge;

import java.io.File;

/**
 * Entry point of the jadx engine. The plugin loads the engine class
 * {@code ir.hanzodev1375.jadx.engine.JadxEngineImpl} from a separate dex file (so jadx can read its
 * own resource files) and talks to it only through this interface.
 */
public interface JadxEngine {

  /**
   * Loads {@code dexFile} and returns a session. Heavy: call it on a background thread.
   *
   * @param outputDir where {@code sources/} will be written
   * @param workDir scratch directory for jadx temp files
   */
  JadxSession open(File dexFile, File outputDir, File workDir, JadxOptions options)
      throws Exception;
}
