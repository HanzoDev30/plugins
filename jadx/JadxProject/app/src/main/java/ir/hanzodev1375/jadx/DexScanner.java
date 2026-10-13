package ir.hanzodev1375.jadx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Finds {@code .dex} files below a folder. Blocking: run it on a background thread. */
final class DexScanner {

  private static final int MAX_DEPTH = 12;
  private static final int MAX_RESULTS = 300;
  private static final int MAX_DIRS = 30000;

  private DexScanner() {}

  static List<DexEntry> scan(File root, AtomicBoolean cancelled) {
    List<DexEntry> found = new ArrayList<>();
    if (root == null || !root.isDirectory()) {
      return found;
    }

    Deque<File> dirs = new ArrayDeque<>();
    Deque<Integer> depths = new ArrayDeque<>();
    dirs.push(root);
    depths.push(0);

    int visited = 0;
    while (!dirs.isEmpty()
        && !cancelled.get()
        && found.size() < MAX_RESULTS
        && visited < MAX_DIRS) {
      File dir = dirs.pop();
      int depth = depths.pop();
      visited++;

      File[] children = dir.listFiles();
      if (children == null) {
        continue;
      }
      for (File child : children) {
        String name = child.getName();
        if (child.isDirectory()) {
          if (depth < MAX_DEPTH && !skipDir(name) && !Files.isSymbolicLink(child.toPath())) {
            dirs.push(child);
            depths.push(depth + 1);
          }
        } else if (name.toLowerCase(Locale.ROOT).endsWith(".dex") && hasDexMagic(child)) {
          found.add(new DexEntry(child));
          if (found.size() >= MAX_RESULTS) {
            break;
          }
        }
      }
    }

    Collections.sort(
        found,
        Comparator.comparing((DexEntry entry) -> entry.file.getAbsolutePath().toLowerCase(Locale.ROOT)));
    return found;
  }

  private static boolean skipDir(String name) {
    return name.equals(".git") || name.equals("node_modules") || name.equals(".gradle");
  }

  /** A real dex starts with the bytes "dex\n". Skips odex/vdex and random files named .dex. */
  private static boolean hasDexMagic(File file) {
    byte[] magic = new byte[4];
    try (InputStream in = new FileInputStream(file)) {
      int read = 0;
      while (read < 4) {
        int n = in.read(magic, read, 4 - read);
        if (n < 0) {
          return false;
        }
        read += n;
      }
    } catch (IOException e) {
      return false;
    }
    return magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x' && magic[3] == '\n';
  }
}
