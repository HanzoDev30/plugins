package ir.ghostide.tailwindcsslsp;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Tailwind class names found inside class="..." attributes into {@link TailwindIconHint}s.
 * Handles the common Tailwind syntax: variant prefixes (md:, hover:), the important marker (!),
 * negative values (-mt-4), opacity modifiers (bg-black/50) and arbitrary values (w-[200px]).
 */
final class TailwindClassScanner {

  private static final Pattern CLASS_ATTR =
      Pattern.compile("(?i)\\bclass\\s*=\\s*([\"'])([^\"']*)\\1");

  private TailwindClassScanner() {}

  static void scan(String text, Set<String> names, List<TailwindIconHint> out) {
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
        if (matches(token, names)) {
          int offset = base + i;
          int line = lineOf(lineStarts, offset);
          out.add(new TailwindIconHint(line, offset - lineStarts[line]));
        }
      }
    }
  }

  private static boolean matches(String token, Set<String> names) {
    String t = trim(token);
    if (t.isEmpty()) return false;

    int colon = lastTopLevelColon(t);
    if (colon >= 0) t = trim(t.substring(colon + 1));
    if (t.isEmpty()) return false;

    int slash = topLevelSlash(t);
    String utility = slash >= 0 ? t.substring(0, slash) : t;

    if (names.contains(utility)) return true;
    if (utility.startsWith("[") && utility.endsWith("]")) return true;
    if (utility.startsWith("-")) {
      String positive = utility.substring(1);
      return names.contains(positive) || names.contains(positive.replaceFirst("^[a-z-]+-\\[", "["));
    }
    return utility.contains("-["); // arbitrary value such as w-[200px]
  }

  private static String trim(String token) {
    String t = token;
    if (t.startsWith("!")) t = t.substring(1);
    return t;
  }

  private static int lastTopLevelColon(String s) {
    int depth = 0;
    int result = -1;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '[' || c == '(') depth++;
      else if (c == ']' || c == ')') depth--;
      else if (c == ':' && depth == 0) result = i;
    }
    return result;
  }

  private static int topLevelSlash(String s) {
    int depth = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '[' || c == '(') depth++;
      else if (c == ']' || c == ')') depth--;
      else if (c == '/' && depth == 0) return i;
    }
    return -1;
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
