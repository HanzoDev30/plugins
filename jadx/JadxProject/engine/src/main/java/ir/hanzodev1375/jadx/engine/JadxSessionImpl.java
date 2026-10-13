package ir.hanzodev1375.jadx.engine;

import ir.hanzodev1375.jadx.bridge.JadxOptions;
import ir.hanzodev1375.jadx.bridge.JadxSession;
import ir.hanzodev1375.jadx.bridge.ProgressListener;
import ir.hanzodev1375.jadx.bridge.SaveResult;

import jadx.api.DecompilationMode;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.impl.NoOpCodeCache;
import jadx.api.utils.tasks.ITaskExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One jadx decompiler instance bound to one dex file. */
final class JadxSessionImpl implements JadxSession {

  private final JadxDecompiler decompiler;
  private final File outDir;
  private final File sourcesDir;
  private final List<String> names;
  private final Map<String, JavaClass> byName;

  private volatile ITaskExecutor running;
  private volatile boolean cancelled;
  private boolean closed;

  JadxSessionImpl(File dex, File outDir, File workDir, JadxOptions options) {
    this.outDir = outDir;
    this.sourcesDir = new File(outDir, "sources");

    JadxArgs args = new JadxArgs();
    args.setInputFile(dex);
    args.setOutDir(outDir);
    args.setOutDirSrc(sourcesDir);
    args.setOutDirRes(new File(outDir, "resources"));
    args.setSkipResources(true); // a .dex has no resources
    args.setCodeCache(NoOpCodeCache.INSTANCE); // do not keep every class in memory on a phone
    args.setPluginLoader(new FixedPluginLoader());
    args.setFilesGetter(new WorkFilesGetter(workDir.toPath()));
    args.setDeobfuscationOn(options.deobfuscation);
    args.setShowInconsistentCode(options.showBadCode);
    args.setDecompilationMode(
        options.fallbackMode ? DecompilationMode.FALLBACK : DecompilationMode.AUTO);
    args.setThreadsCount(options.threads > 0 ? options.threads : defaultThreads());

    JadxDecompiler jadx = new JadxDecompiler(args);
    List<String> classNames = new ArrayList<>();
    Map<String, JavaClass> map = new HashMap<>();
    try {
      jadx.load();
      for (JavaClass cls : jadx.getClasses()) {
        if (cls.isNoCode()) {
          continue;
        }
        String name = cls.getFullName();
        if (map.put(name, cls) == null) {
          classNames.add(name);
        }
      }
    } catch (RuntimeException | Error e) {
      try {
        jadx.close();
      } catch (Throwable ignored) {
        // keep the original failure
      }
      throw e;
    }
    Collections.sort(classNames);
    this.decompiler = jadx;
    this.names = Collections.unmodifiableList(classNames);
    this.byName = map;
  }

  private static int defaultThreads() {
    int cpus = Runtime.getRuntime().availableProcessors();
    return Math.max(1, Math.min(cpus / 2, 4));
  }

  @Override
  public int classCount() {
    return names.size();
  }

  @Override
  public List<String> classNames() {
    return names;
  }

  @Override
  public String decompileClass(String className) {
    JavaClass cls = byName.get(className);
    if (cls == null) {
      throw new IllegalArgumentException("Unknown class: " + className);
    }
    return cls.getCode();
  }

  @Override
  public String sourcePathOf(String className) {
    return className.replace('.', '/') + ".java";
  }

  @Override
  public File sourcesDir() {
    return sourcesDir;
  }

  @Override
  public SaveResult saveAll(ProgressListener listener) {
    long start = System.currentTimeMillis();
    cancelled = false;
    ITaskExecutor executor = decompiler.getSaveTaskExecutor();
    running = executor;
    int total = 0;
    try {
      executor.execute();
      total = Math.max(1, executor.getTasksCount());
      while (executor.isRunning()) {
        listener.onProgress(executor.getProgress(), total);
        Thread.sleep(300);
      }
      executor.awaitTermination();
      listener.onProgress(executor.getProgress(), total);
    } catch (InterruptedException e) {
      executor.terminate();
      cancelled = true;
      Thread.currentThread().interrupt();
    } finally {
      running = null;
    }
    removeEmptyDir(new File(outDir, "resources"));
    return new SaveResult(
        names.size(),
        decompiler.getErrorsCount(),
        decompiler.getWarnsCount(),
        cancelled,
        System.currentTimeMillis() - start);
  }

  @Override
  public void cancel() {
    cancelled = true;
    ITaskExecutor executor = running;
    if (executor != null) {
      executor.terminate();
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    cancel();
    ITaskExecutor executor = running;
    if (executor != null) {
      executor.awaitTermination();
    }
    try {
      decompiler.close();
    } catch (Throwable ignored) {
      // closing is best effort
    }
  }

  private static void removeEmptyDir(File dir) {
    String[] children = dir.list();
    if (children != null && children.length == 0) {
      dir.delete();
    }
  }
}
