package ir.hanzodev1375.javaicons;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves {@code R.color.*} / {@code @color/...} and {@code R.string.*} / {@code @string/...}
 * against the project's {@code res/values*} folders.
 *
 * <p>Colors are parsed from {@code <color>} items and {@code res/color/*.xml} selectors. Strings are
 * picked from the best matching {@code values-*} qualifier for the current device locale, mimicking
 * Android's resource resolution (language/region only). All parsing happens once per {@code resDir}
 * (or on explicit {@link #invalidate()}) and is cached.
 */
public final class ResValues {

  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String ANDROID_COLOR_PREFIX = "@android:color/";
  private static final int INAPPLICABLE = Integer.MIN_VALUE;
  private static final int MAX_STRING_DISPLAY = 60;

  private final Context context;

  private File resDir;
  private boolean loaded;
  private Map<String, Integer> colors = Collections.emptyMap();
  private Map<String, String> strings = Collections.emptyMap();

  public ResValues(Context context) {
    this.context = context;
  }

  public synchronized void setResDir(File res) {
    if (res != null && res.equals(resDir)) return;
    resDir = res;
    loaded = false;
  }

  public synchronized void invalidate() {
    loaded = false;
  }

  public synchronized Integer color(String name) {
    ensureLoaded();
    return colors.get(name);
  }

  public synchronized String string(String name) {
    ensureLoaded();
    return strings.get(name);
  }

  // ------- loading -------

  private void ensureLoaded() {
    if (loaded) return;
    Map<String, String> rawColors = new HashMap<>();
    Map<String, String> rawStrings = new HashMap<>();

    for (File dir : valuesDirs()) {
      File[] files = dir.listFiles();
      if (files == null) continue;
      for (File f : files) {
        if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
          parseValuesFile(f, rawColors, rawStrings);
        }
      }
    }

    File res = resDir;
    if (res != null) {
      File colorDir = new File(res, "color");
      if (colorDir.isDirectory()) {
        File[] files = colorDir.listFiles();
        if (files != null) {
          for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) continue;
            String value = parseSelector(f);
            if (value != null) {
              rawColors.put(f.getName().substring(0, f.getName().length() - 4), value);
            }
          }
        }
      }
    }

    Map<String, Integer> resolved = new HashMap<>();
    for (String name : rawColors.keySet()) {
      Integer c = resolveColor(rawColors, name, new HashSet<String>());
      if (c != null) resolved.put(name, c);
    }

    Map<String, String> decoded = new HashMap<>();
    for (Map.Entry<String, String> e : rawStrings.entrySet()) {
      if (e.getValue() != null) decoded.put(e.getKey(), unescape(e.getValue()));
    }

    colors = resolved;
    strings = decoded;
    loaded = true;
  }

  // ------- values*/ qualifier selection -------

  private List<File> valuesDirs() {
    File res = resDir;
    if (res == null || !res.isDirectory()) return Collections.emptyList();
    File[] children = res.listFiles();
    if (children == null) return Collections.emptyList();

    final Locale locale = currentLocale();

    List<File> out = new ArrayList<>();
    for (File c : children) {
      if (!c.isDirectory() || !c.getName().startsWith("values")) continue;
      if (score(c.getName(), locale) == INAPPLICABLE) continue;
      out.add(c);
    }
    Collections.sort(
        out,
        new Comparator<File>() {
          @Override
          public int compare(File a, File b) {
            int sa = score(a.getName(), locale);
            int sb = score(b.getName(), locale);
            if (sa != sb) return Integer.compare(sa, sb);
            int la = a.getName().length();
            int lb = b.getName().length();
            if (la != lb) return Integer.compare(la, lb);
            return a.getName().compareTo(b.getName());
          }
        });
    return out;
  }

  private Locale currentLocale() {
    try {
      Configuration cfg = context.getResources().getConfiguration();
      if (!cfg.getLocales().isEmpty()) return cfg.getLocales().get(0);
    } catch (Throwable ignored) {
    }
    return Locale.getDefault();
  }

  /**
   * Higher score wins. {@code values} (no qualifier) scores 0. Only the locale language/region
   * qualifiers are considered; theme (night/notnight) folders are ignored so inlays never follow the
   * app theme.
   */
  private static int score(String dirName, Locale target) {
    String q = dirName.length() > 7 ? dirName.substring(7) : "";
    if (q.isEmpty()) return 0;

    String[] toks = q.split("-");
    for (String tok : toks) {
      if ("night".equals(tok) || "notnight".equals(tok)) return INAPPLICABLE;
    }

    String lang = null;
    String region = null;
    String first = toks[0];
    if (first.startsWith("b+")) {
      String[] parts = first.substring(2).split("\\+");
      if (parts.length >= 1 && isLanguage(parts[0])) {
        lang = parts[0];
        for (int i = 1; i < parts.length; i++) {
          if (parts[i].length() == 2) {
            region = parts[i];
            break;
          }
        }
      }
    } else if (isLanguage(first)) {
      lang = first;
      if (toks.length >= 2 && toks[1].length() == 3 && toks[1].charAt(0) == 'r') {
        region = toks[1].substring(1);
      }
    }

    if (lang == null) return 0;
    if (!lang.equalsIgnoreCase(target.getLanguage())) return INAPPLICABLE;
    String tr = target.getCountry();
    if (region == null) return 2;
    if (region.equalsIgnoreCase(tr)) return 3;
    if (tr.isEmpty()) return 1;
    return INAPPLICABLE;
  }

  private static boolean isLanguage(String s) {
    if (s == null || s.length() < 2 || s.length() > 3) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 'a' || c > 'z') return false;
    }
    return true;
  }

  // ------- XML parsing -------

  private void parseValuesFile(File f, Map<String, String> colors, Map<String, String> strings) {
    FileInputStream in = null;
    try {
      in = new FileInputStream(f);
      XmlPullParser p = Xml.newPullParser();
      p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      p.setInput(in, "utf-8");
      int ev = p.getEventType();
      while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
          String tag = p.getName();
          String name = p.getAttributeValue(null, "name");
          if (name != null && "color".equals(tag)) {
            colors.put(name, readText(p).trim());
          } else if (name != null && "string".equals(tag)) {
            strings.put(name, readText(p));
          } else if (name != null && "item".equals(tag)) {
            String type = p.getAttributeValue(null, "type");
            if ("color".equals(type)) {
              colors.put(name, readText(p).trim());
            } else if ("string".equals(type)) {
              strings.put(name, readText(p));
            } else {
              skip(p);
            }
          }
        }
        ev = p.next();
      }
    } catch (Throwable ignored) {
    } finally {
      close(in);
    }
  }

  private String parseSelector(File f) {
    FileInputStream in = null;
    String defaultColor = null;
    String firstColor = null;
    try {
      in = new FileInputStream(f);
      XmlPullParser p = Xml.newPullParser();
      p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      p.setInput(in, "utf-8");
      int ev = p.getEventType();
      while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG && "item".equals(p.getName())) {
          boolean hasState = false;
          int count = p.getAttributeCount();
          for (int i = 0; i < count; i++) {
            String an = p.getAttributeName(i);
            if (an != null && an.startsWith("state")) {
              hasState = true;
              break;
            }
          }
          String c = p.getAttributeValue(ANDROID_NS, "color");
          if (c == null) c = p.getAttributeValue(null, "color");
          if (c != null) {
            c = c.trim();
            if (firstColor == null) firstColor = c;
            if (!hasState && defaultColor == null) defaultColor = c;
          }
        }
        ev = p.next();
      }
    } catch (Throwable ignored) {
    } finally {
      close(in);
    }
    return defaultColor != null ? defaultColor : firstColor;
  }

  private static String readText(XmlPullParser p) throws Exception {
    StringBuilder sb = new StringBuilder();
    int depth = 1;
    while (depth > 0) {
      int ev = p.next();
      if (ev == XmlPullParser.START_TAG) {
        depth++;
      } else if (ev == XmlPullParser.END_TAG) {
        depth--;
      } else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
        String t = p.getText();
        if (t != null) sb.append(t);
      } else if (ev == XmlPullParser.END_DOCUMENT) {
        break;
      }
    }
    return sb.toString();
  }

  private static void skip(XmlPullParser p) throws Exception {
    int depth = 1;
    while (depth > 0) {
      int ev = p.next();
      if (ev == XmlPullParser.START_TAG) {
        depth++;
      } else if (ev == XmlPullParser.END_TAG) {
        depth--;
      } else if (ev == XmlPullParser.END_DOCUMENT) {
        break;
      }
    }
  }

  private static void close(Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (Throwable ignored) {
      }
    }
  }

  // ------- color resolution -------

  private static Integer resolveColor(Map<String, String> raw, String name, Set<String> seen) {
    if (name == null || !seen.add(name)) return null;
    return parseColorValue(raw, raw.get(name), seen);
  }

  private static Integer parseColorValue(
      Map<String, String> raw, String value, Set<String> seen) {
    if (value == null) return null;
    String v = value.trim();
    if (v.isEmpty()) return null;
    if (v.charAt(0) == '#') return parseHex(v.substring(1));
    if (v.startsWith("@color/")) return resolveColor(raw, v.substring(7).trim(), seen);
    if (v.startsWith(ANDROID_COLOR_PREFIX)) {
      return frameworkColor(v.substring(ANDROID_COLOR_PREFIX.length()).trim());
    }
    return null;
  }

  private static Integer parseHex(String h) {
    h = h.trim();
    int len = h.length();
    try {
      if (len == 3) {
        int r = Integer.parseInt(h.substring(0, 1), 16);
        int g = Integer.parseInt(h.substring(1, 2), 16);
        int b = Integer.parseInt(h.substring(2, 3), 16);
        return 0xFF000000 | (r * 0x11 << 16) | (g * 0x11 << 8) | (b * 0x11);
      } else if (len == 4) {
        int a = Integer.parseInt(h.substring(0, 1), 16);
        int r = Integer.parseInt(h.substring(1, 2), 16);
        int g = Integer.parseInt(h.substring(2, 3), 16);
        int b = Integer.parseInt(h.substring(3, 4), 16);
        return (a * 0x11 << 24) | (r * 0x11 << 16) | (g * 0x11 << 8) | (b * 0x11);
      } else if (len == 6) {
        return 0xFF000000 | (int) Long.parseLong(h, 16);
      } else if (len == 8) {
        return (int) Long.parseLong(h, 16);
      }
    } catch (NumberFormatException ignored) {
    }
    return null;
  }

  private static Integer frameworkColor(String name) {
    if (name == null) return null;
    switch (name) {
      case "white":
        return 0xFFFFFFFF;
      case "black":
        return 0xFF000000;
      case "transparent":
        return 0x00000000;
      case "red":
        return 0xFFFF0000;
      case "green":
        return 0xFF00FF00;
      case "blue":
        return 0xFF0000FF;
      case "yellow":
        return 0xFFFFFF00;
      case "cyan":
        return 0xFF00FFFF;
      case "magenta":
        return 0xFFFF00FF;
      case "gray":
        return 0xFF888888;
      case "dkgray":
        return 0xFF444444;
      case "ltgray":
        return 0xFFCCCCCC;
      default:
        return null;
    }
  }

  // ------- string helpers -------

  private static String unescape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(i + 1);
        switch (n) {
          case 'n':
            sb.append('\n');
            i++;
            break;
          case 't':
            sb.append('\t');
            i++;
            break;
          case 'r':
            sb.append('\r');
            i++;
            break;
          case 'u':
            if (i + 5 < s.length()) {
              try {
                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 5;
              } catch (NumberFormatException e) {
                sb.append(n);
                i++;
              }
            } else {
              sb.append(n);
              i++;
            }
            break;
          default:
            sb.append(n);
            i++;
            break;
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Collapses whitespace/newlines and truncates so the inlay stays a readable width. */
  public static String displayString(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length());
    boolean space = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
        if (!space) {
          sb.append(' ');
          space = true;
        }
      } else {
        sb.append(c);
        space = false;
      }
    }
    String t = sb.toString().trim();
    if (t.length() > MAX_STRING_DISPLAY) t = t.substring(0, MAX_STRING_DISPLAY) + "\u2026";
    return t;
  }
}
