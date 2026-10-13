package ir.hanzodev1375.jadx;

import java.io.File;
import java.util.Locale;

/** A dex file found on disk. */
final class DexEntry {

  final File file;
  final long size;

  DexEntry(File file) {
    this.file = file;
    this.size = file.length();
  }

  String sizeText() {
    if (size < 1024) {
      return size + " B";
    }
    if (size < 1024L * 1024L) {
      return String.format(Locale.US, "%.1f KB", size / 1024.0);
    }
    return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
  }

  /** Path shown under the file name, relative to {@code root} when possible. */
  String relativeTo(File root) {
    String base = root.getAbsolutePath();
    String full = file.getAbsolutePath();
    if (full.startsWith(base) && full.length() > base.length() + 1) {
      return full.substring(base.length() + 1);
    }
    return full;
  }
}
