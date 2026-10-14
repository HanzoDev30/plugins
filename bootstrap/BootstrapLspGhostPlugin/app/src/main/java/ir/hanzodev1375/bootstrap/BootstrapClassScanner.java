package ir.hanzodev1375.bootstrap;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns Bootstrap class names found inside class="..." attributes into {@link BootstrapIconHint}s. */
final class BootstrapClassScanner {

  private static final Pattern CLASS_ATTR =
      Pattern.compile("(?i)\\bclass\\s*=\\s*([\"'])([^\"']*)\\1");

  private BootstrapClassScanner() {}

  static void scan(String text, Set<String> names, List<BootstrapIconHint> out) {
    if (text == null || text.isEmpty() || names.isEmpty()) return;

    int[] lineStarts = lineStarts(text);
    Matcher attr = CLASS_ATTR.matcher(text);
    while (attr.find()) {
      String value = attr.group(2);
      int base = attr.start(2);
      int i = 0;
      while (i < value.length()) {
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        int start = i;
        while (i < value.length() && !Character.isWhitespace(value.charAt(i))) i++;
        if (i == start) break;

        String token = value.substring(start, i);
        if (names.contains(token)) {
          int offset = base + i;
          int line = lineOf(lineStarts, offset);
          out.add(new BootstrapIconHint(line, offset - lineStarts[line]));
        }
      }
    }
  }

  private static int[] lineStarts(String text) {
    int count = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    int[] starts = new int[count];
    int index = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') starts[index++] = i + 1;
    }
    return starts;
  }

  private static int lineOf(int[] starts, int offset) {
    int lo = 0;
    int hi = starts.length - 1;
    while (lo < hi) {
      int mid = (lo + hi + 1) >>> 1;
      if (starts[mid] <= offset) {
        lo = mid;
      } else {
        hi = mid - 1;
      }
    }
    return lo;
  }
}
