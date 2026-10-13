package ir.hanzodev1375.jadx.bridge;

import java.io.Closeable;
import java.io.File;
import java.util.List;

/**
 * One loaded dex file. All methods are blocking and must be called off the main thread. The
 * implementation lives in the engine module and is loaded through its own class loader.
 */
public interface JadxSession extends Closeable {

  /** Number of top-level classes that can be decompiled. */
  int classCount();

  /** Sorted full names (e.g. {@code com.example.Main}) of the top-level classes. */
  List<String> classNames();

  /** Decompiles one class and returns its Java source. */
  String decompileClass(String className) throws Exception;

  /** Path of the class source relative to {@link #sourcesDir()}, e.g. {@code com/example/Main.java}. */
  String sourcePathOf(String className);

  /** Directory where "decompile all" (and the class browser) write their {@code .java} files. */
  File sourcesDir();

  /** Decompiles every class into {@link #sourcesDir()}. Blocks until finished or {@link #cancel()}. */
  SaveResult saveAll(ProgressListener listener) throws Exception;

  /** Stops a running {@link #saveAll}. Safe to call from any thread. */
  void cancel();

  @Override
  void close();
}
