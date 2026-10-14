package ir.hanzodev1375.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors the index that assets/bootstrap-lsp.js builds: it reads the project's own
 * node_modules/bootstrap/dist/css/bootstrap.css (falling back to bootstrap.min.css) and extracts
 * every class name from it. Because the file is re-read whenever it changes, the icons follow the
 * installed Bootstrap version instead of a hard-coded list.
 */
final class BootstrapClassIndex {

  private static final String[] RELATIVE = {
    "node_modules/bootstrap/dist/css/bootstrap.css",
    "node_modules/bootstrap/dist/css/bootstrap.min.css",
  };

  private static final String[] ABSOLUTE = {
    "/opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.css",
    "/opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.min.css",
  };

  private static final int MAX_PARENT_LEVELS = 8;
  private static final long MAX_BYTES = 4L * 1024 * 1024;

  private static final Pattern RULE = Pattern.compile("([^{}]+)\\{[^{}]*\\}");
  private static final Pattern CLASS = Pattern.compile("\\.(-?[_a-zA-Z][\\w-]*)");

  private final Set<String> names = new HashSet<>();
  private File source;
  private long stamp = Long.MIN_VALUE;

  Set<String> names() {
    return names;
  }

  File source() {
    return source;
  }

  /** Reloads the class list when the underlying css changed; safe to call on every rescan. */
  void refresh(File baseDir, File projectRoot, File fallbackCss) {
    File css = find(baseDir, projectRoot);
    if (css == null && fallbackCss != null && fallbackCss.isFile()) css = fallbackCss;
    if (css == null) {
      for (String path : ABSOLUTE) {
        File f = new File(path);
        if (f.isFile()) {
          css = f;
          break;
        }
      }
    }
    if (css == null) {
      if (!names.isEmpty()) {
        names.clear();
        source = null;
        stamp = Long.MIN_VALUE;
      }
      return;
    }
    long mtime = css.lastModified();
    if (css.equals(source) && mtime == stamp) return;

    Set<String> parsed = parse(css);
    if (!parsed.isEmpty() || names.isEmpty()) {
      names.clear();
      names.addAll(parsed);
    }
    source = css;
    stamp = mtime;
  }

  private static File find(File baseDir, File projectRoot) {
    File found = search(baseDir);
    if (found != null) return found;
    return search(projectRoot);
  }

  private static File search(File start) {
    File dir = start;
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++) {
      for (String rel : RELATIVE) {
        File f = new File(dir, rel);
        if (f.isFile()) return f;
      }
      dir = dir.getParentFile();
    }
    return null;
  }

  private static Set<String> parse(File file) {
    Set<String> out = new HashSet<>();
    String css = read(file);
    if (css.isEmpty()) return out;
    css = css.replaceAll("(?s)/\\*.*?\\*/", "");

    Matcher rule = RULE.matcher(css);
    while (rule.find()) {
      String selector = rule.group(1).trim();
      if (selector.isEmpty() || selector.charAt(0) == '@') continue;
      for (String part : selector.split(",")) {
        Matcher cls = CLASS.matcher(part.trim());
        while (cls.find()) out.add(cls.group(1));
      }
    }
    return out;
  }

  private static String read(File file) {
    try (InputStream in = new FileInputStream(file)) {
      byte[] bytes = in.readAllBytes();
      if (bytes.length > MAX_BYTES) return "";
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }
}
