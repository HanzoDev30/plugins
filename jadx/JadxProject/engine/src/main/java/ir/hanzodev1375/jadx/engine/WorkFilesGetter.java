package ir.hanzodev1375.jadx.engine;

import jadx.core.plugins.files.IJadxFilesGetter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps jadx config/cache/temp files inside our own work directory. */
final class WorkFilesGetter implements IJadxFilesGetter {

  private final Path root;

  WorkFilesGetter(Path root) {
    this.root = root;
  }

  @Override
  public Path getConfigDir() {
    return sub("config");
  }

  @Override
  public Path getCacheDir() {
    return sub("cache");
  }

  @Override
  public Path getTempDir() {
    return sub("tmp");
  }

  private Path sub(String name) {
    Path dir = root.resolve(name);
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return dir;
  }
}
