package ir.hanzodev1375.htmlicons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds local image references in web sources and turns them into {@link HtmlIconHint}s.
 *
 * <p>Any token ending in a known image extension ({@code .svg .png .jpg .jpeg .gif .webp .bmp
 * .avif}) is a candidate, including relative paths like {@code ./hsi.svg}, {@code ../assets/x.png}
 * or {@code /assets/logo.svg} and multi-segment paths. The path is resolved against the open file's
 * directory first, then the project root; only existing files produce a hint.
 *
 * <p>Comments ({@code //}, {@code /*} ... {@code *}{@code /} and {@code <!-- -->}) are ignored.
 */
public final class WebImageScanner {

  private static final Pattern P_IMG =
      Pattern.compile(
          "(?i)(?<![A-Za-z0-9_.-/])([A-Za-z0-9_./-]+\\.(?:svg|png|jpe?g|gif|webp|bmp|avif))\\b");

  private WebImageScanner() {}

  public static void scan(
      String text, File baseDir, File projectRoot, WebImageCache cache, List<HtmlIconHint> out) {
    if (text == null || text.isEmpty()) return;
    String[] lines = text.split("\n", -1);
    Mask mask = new Mask();
    boolean carry = false;
    for (int li = 0; li < lines.length; li++) {
      String line = lines[li];
      exclude(line, carry, mask);
      carry = mask.carry;

      Matcher m = P_IMG.matcher(line);
      while (m.find()) {
        int col = m.start();
        if (masked(col, mask)) continue;
        File f = resolve(m.group(1), baseDir, projectRoot);
        if (f == null) continue;
        cache.preload(f);
        out.add(new HtmlIconHint(li, m.end(), f.getAbsolutePath()));
      }
    }
  }

  private static File resolve(String ref, File baseDir, File projectRoot) {
    String s = ref.trim();
    if (s.startsWith("./")) {
      s = s.substring(2);
    }
    File direct = new File(baseDir, s);
    if (direct.isFile()) return direct;

    String rootRel = s.startsWith("/") ? s.substring(1) : s;
    if (projectRoot != null) {
      File rooted = new File(projectRoot, rootRel);
      if (rooted.isFile()) return rooted;
    }
    return null;
  }

  private static boolean masked(int col, Mask mask) {
    for (int[] r : mask.ranges) {
      if (col >= r[0] && col < r[1]) return true;
    }
    return false;
  }

  /** Marks comment spans while leaving string literals visible (image paths live inside them). */
  private static void exclude(String line, boolean carry, Mask out) {
    int i = 0;
    int n = line.length();
    int state = carry ? 2 : 0; // 0=none 1=line 2=block /* */ 3=html <!-- -->
    int segStart = carry ? 0 : -1;
    while (i < n) {
      char c = line.charAt(i);
      if (state == 2) {
        if (c == '*' && i + 1 < n && line.charAt(i + 1) == '/') {
          if (segStart >= 0) out.ranges.add(new int[] {segStart, i + 2});
          state = 0;
          segStart = -1;
          i += 2;
          continue;
        }
        i++;
        continue;
      }
      if (state == 3) {
        if (c == '-' && i + 2 < n && line.charAt(i + 1) == '-' && line.charAt(i + 2) == '>') {
          if (segStart >= 0) out.ranges.add(new int[] {segStart, i + 3});
          state = 0;
          segStart = -1;
          i += 3;
          continue;
        }
        i++;
        continue;
      }
      if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
        out.ranges.add(new int[] {i, n});
        state = 1;
        break;
      }
      if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
        segStart = i;
        state = 2;
        i += 2;
        continue;
      }
      if (c == '<' && i + 3 < n && line.charAt(i + 1) == '!' && line.charAt(i + 2) == '-'
          && line.charAt(i + 3) == '-') {
        segStart = i;
        state = 3;
        i += 4;
        continue;
      }
      i++;
    }
    if (state == 2 || state == 3) {
      if (segStart >= 0) out.ranges.add(new int[] {segStart, n});
      out.carry = true;
    }
  }

  private static final class Mask {
    final List<int[]> ranges = new ArrayList<>();
    boolean carry;
  }
}