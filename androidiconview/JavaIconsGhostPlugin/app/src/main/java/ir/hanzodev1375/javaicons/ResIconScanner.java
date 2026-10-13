package ir.hanzodev1375.javaicons;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.lang.styling.color.ConstColor;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;


/**
 * Finds resource references in Java and XML text and turns them into inlay hints:
 *
 * <ul>
 *   <li>{@code R.drawable.*} / {@code R.mipmap.*} / {@code @drawable/*} / {@code @mipmap/*} → an
 *       icon preview ({@link ResIconHint}).
 *   <li>{@code R.color.*} / {@code @color/*} → a color swatch ({@link ColorInlayHint}).
 *   <li>{@code R.string.*} / {@code @string/*} → the localized string ({@link TextInlayHintCompat}).
 * </ul>
 */
public final class ResIconScanner {

  private static final Pattern P_RES =
      Pattern.compile("\\bR\\.(drawable|mipmap)\\.([a-z][a-z0-9_]*)\\b");

  private static final Pattern P_COLOR = Pattern.compile("\\bR\\.color\\.([a-z][a-z0-9_]*)\\b");

  private static final Pattern P_STRING = Pattern.compile("\\bR\\.string\\.([a-z][a-z0-9_]*)\\b");

  /**
   * XML resource references, e.g. {@code @drawable/ic_launcher}, {@code @mipmap/ic_launcher}.
   * Framework references such as {@code @android:drawable/...} do not match (a name follows the
   * {@code @} directly here) and theme references {@code ?attr/...} are unrelated.
   */
  private static final Pattern P_XML_RES =
      Pattern.compile("@(drawable|mipmap)/([a-z][a-z0-9_]*)\\b");

  private static final Pattern P_XML_COLOR = Pattern.compile("@color/([a-z][a-z0-9_]*)\\b");

  private static final Pattern P_XML_STRING = Pattern.compile("@string/([a-z][a-z0-9_]*)\\b");

  private ResIconScanner() {}

  public static List<InlayHint> scan(String text, ResIcons icons, ResValues values) {
    List<InlayHint> out = new ArrayList<>();
    if (text == null || text.isEmpty()) return out;
    String[] lines = text.split("\n", -1);
    Set<String> seen = new HashSet<>();
    boolean carry = false;
    for (int li = 0; li < lines.length; li++) {
      String line = lines[li];
      Mask mask = new Mask();
      exclude(line, carry, mask);
      carry = mask.carry;

      if (icons != null) {
        Matcher m = P_RES.matcher(line);
        while (m.find()) {
          int col = m.start();
          if (masked(col, mask)) continue;
          File f = icons.find(m.group(2));
          if (f == null) continue;
          icons.preload(f);
          if (seen.add(li + ":" + col)) {
            out.add(new ResIconHint(li, col, f.getAbsolutePath()));
          }
        }
        Matcher m2 = P_XML_RES.matcher(line);
        while (m2.find()) {
          int col = m2.start();
          if (masked(col, mask)) continue;
          File f = icons.find(m2.group(2));
          if (f == null) continue;
          icons.preload(f);
          if (seen.add(li + ":" + col)) {
            out.add(new ResIconHint(li, col, f.getAbsolutePath()));
          }
        }
      }

      if (values != null) {
        Matcher mc = P_COLOR.matcher(line);
        while (mc.find()) {
          int col = mc.start();
          if (masked(col, mask)) continue;
          Integer argb = values.color(mc.group(1));
          if (argb == null) continue;
          if (seen.add(li + ":" + col)) {
            out.add(new ColorInlayHintCompat(li, mc.end(), new ConstColor(argb)));
          }
        }
        Matcher mc2 = P_XML_COLOR.matcher(line);
        while (mc2.find()) {
          int col = mc2.start();
          if (masked(col, mask)) continue;
          Integer argb = values.color(mc2.group(1));
          if (argb == null) continue;
          if (seen.add(li + ":" + col)) {
            out.add(new ColorInlayHintCompat(li, mc2.end(), new ConstColor(argb)));
          }
        }
        Matcher ms = P_STRING.matcher(line);
        while (ms.find()) {
          int col = ms.start();
          if (masked(col, mask)) continue;
          String value = values.string(ms.group(1));
          if (value == null || value.isEmpty()) continue;
          if (seen.add(li + ":" + col)) {
            out.add(new TextInlayHintCompat(li, ms.end(), ResValues.displayString(value)));
          }
        }
        Matcher ms2 = P_XML_STRING.matcher(line);
        while (ms2.find()) {
          int col = ms2.start();
          if (masked(col, mask)) continue;
          String value = values.string(ms2.group(1));
          if (value == null || value.isEmpty()) continue;
          if (seen.add(li + ":" + col)) {
            out.add(new TextInlayHintCompat(li, ms2.end(), ResValues.displayString(value)));
          }
        }
      }
    }
    return out;
  }

  private static boolean masked(int col, Mask mask) {
    for (int[] r : mask.ranges) {
      if (col >= r[0] && col < r[1]) return true;
    }
    return false;
  }

  private static void exclude(String line, boolean carry, Mask out) {
    int i = 0;
    int n = line.length();
    boolean inBlk = carry;
    int segStart = carry ? 0 : -1;
    while (i < n) {
      char c = line.charAt(i);
      if (inBlk) {
        if (c == '*' && i + 1 < n && line.charAt(i + 1) == '/') {
          if (segStart >= 0) out.ranges.add(new int[] {segStart, i + 2});
          inBlk = false;
          segStart = -1;
          i += 2;
        } else {
          i++;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        char q = c;
        i++;
        while (i < n) {
          char d = line.charAt(i);
          if (d == '\\') {
            i += 2;
          } else if (d == q) {
            i++;
            break;
          } else {
            i++;
          }
        }
        continue;
      }
      if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
        out.ranges.add(new int[] {i, n});
        break;
      }
      if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
        segStart = i;
        inBlk = true;
        i += 2;
        continue;
      }
      i++;
    }
    if (inBlk) {
      if (segStart >= 0) out.ranges.add(new int[] {segStart, n});
      out.carry = true;
    }
  }

  private static final class Mask {
    final List<int[]> ranges = new ArrayList<>();
    boolean carry;
  }
}
