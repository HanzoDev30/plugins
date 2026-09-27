package ir.ghostide.composerlsp.checker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.rosemoe.sora.text.Content;
import ir.hanzodev1375.ghostide.codeeditors.dependencychecker.DependencyMatch;

/**
 * Line oriented parser for the {@code require} / {@code require-dev} tables of a
 * {@code composer.json}.
 *
 * <p>Every returned {@link DependencyMatch} reuses the host's record so the checker can feed the
 * editor exactly like the built-in Gradle/TOML checkers do: {@code group} carries the Composer
 * vendor, {@code name} the package, and {@code version} the raw constraint. The column span points
 * at the constraint <em>inside</em> its quotes, which is exactly the token that gets highlighted
 * and later rewritten.
 *
 * <p>Platform requirements ({@code php}, {@code ext-*}, {@code lib-*}, {@code composer-plugin-api},
 * …) are skipped: they are provided by the runtime, never by Packagist.
 */
public final class ComposerManifestParser {

  private static final Set<String> DEPENDENCY_SECTIONS = Set.of("require", "require-dev");

  private ComposerManifestParser() {}

  /** True when this parser is responsible for the given file. */
  public static boolean handles(String path) {
    if (path == null) {
      return false;
    }
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String name = slash < 0 ? normalized : normalized.substring(slash + 1);
    return "composer.json".equals(name);
  }

  /**
   * Marks which of the lines {@code [first, last]} hold dependency entries, in one forward pass
   * over the manifest. The result is indexed by {@code line - first}.
   *
   * <p>Entries always sit directly inside a top-level object, so a single brace counter plus the
   * depth of the first object is enough to tell whether the current section is one of ours; going
   * back to the top of the file is what makes this safe to call again after every keystroke.
   */
  public static boolean[] dependencyLines(Content text, int first, int last) {
    int size = Math.max(0, last - first + 1);
    boolean[] flags = new boolean[size];
    if (size == 0) {
      return flags;
    }
    int depth = 0;
    int entryDepth = -1;
    boolean inSection = false;
    int limit = Math.min(last, text.getLineCount() - 1);
    for (int i = 0; i <= limit; i++) {
      String raw = text.getLineString(i);
      // Whether this line is an entry is decided by the state left behind by the lines above it,
      // so the flag is taken before this line gets a chance to open the next section.
      if (i >= first) {
        flags[i - first] = depth == entryDepth && inSection;
      }
      String key = topLevelKey(raw);
      if (key != null && netBraces(raw) > 0) {
        if (entryDepth < 0) {
          // The first object of a manifest is its root; what lives inside it sets the entry depth.
          entryDepth = depth + 1;
          inSection = DEPENDENCY_SECTIONS.contains(key);
        } else if (depth == entryDepth - 1) {
          inSection = DEPENDENCY_SECTIONS.contains(key);
        }
      }
      depth = Math.max(0, depth + netBraces(raw));
    }
    return flags;
  }

  /** Collects every checkable dependency declared on one line into {@code into}. */
  public static void collectLine(String lineText, int line, List<DependencyMatch> into) {
    if (line < 0) {
      return;
    }
    int length = lineText.length();
    int index = 0;
    while (index < length) {
      int keyStart = lineText.indexOf('"', index);
      if (keyStart < 0) {
        break;
      }
      int keyEnd = lineText.indexOf('"', keyStart + 1);
      if (keyEnd < 0) {
        break;
      }
      int colon = lineText.indexOf(':', keyEnd + 1);
      if (colon < 0) {
        break;
      }
      int valueStart = firstNonSpace(lineText, colon + 1);
      if (valueStart >= length || lineText.charAt(valueStart) != '"') {
        // Not a "name": "constraint" pair — skip past the key and keep looking.
        index = keyEnd + 1;
        continue;
      }
      int valueEnd = lineText.indexOf('"', valueStart + 1);
      if (valueEnd < 0) {
        break;
      }
      addMatch(
          lineText.substring(keyStart + 1, keyEnd),
          lineText.substring(valueStart + 1, valueEnd),
          line,
          valueStart + 1,
          valueEnd,
          into);
      index = valueEnd + 1;
    }
  }

  /** Returns the dependency whose constraint token sits under {@code column}, or null. */
  public static DependencyMatch findAt(Content text, int line, int column) {
    if (line < 0 || line >= text.getLineCount()) {
      return null;
    }
    if (!dependencyLines(text, line, line)[0]) {
      return null;
    }
    List<DependencyMatch> matches = new ArrayList<>(1);
    collectLine(text.getLineString(line), line, matches);
    for (DependencyMatch match : matches) {
      if (column >= match.versionStart() && column <= match.versionEnd()) {
        return match;
      }
    }
    return null;
  }

  /** Returns the key of a line shaped like {@code "name": …}, or null for anything else. */
  private static String topLevelKey(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || trimmed.charAt(0) != '"') {
      return null;
    }
    int keyEnd = trimmed.indexOf('"', 1);
    if (keyEnd < 0) {
      return null;
    }
    return trimmed.substring(1, keyEnd);
  }

  /** Counts {@code {}} outside string literals, so a description cannot shift the depth. */
  private static int netBraces(String raw) {
    int net = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        net++;
      } else if (c == '}') {
        net--;
      }
    }
    return net;
  }

  private static int firstNonSpace(String text, int from) {
    int index = from;
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
      index++;
    }
    return index;
  }

  private static void addMatch(
      String name, String constraint, int line, int start, int end, List<DependencyMatch> into) {
    if (name.isEmpty() || constraint.isEmpty() || start >= end) {
      return;
    }
    if (DEPENDENCY_SECTIONS.contains(name)) {
      return;
    }
    int slash = name.indexOf('/');
    if (slash <= 0 || slash == name.length() - 1) {
      return;
    }
    if (name.indexOf('/', slash + 1) >= 0) {
      return;
    }
    String vendor = name.substring(0, slash);
    String packageName = name.substring(slash + 1);
    if (isPlatformRequirement(packageName)) {
      return;
    }
    into.add(new DependencyMatch(vendor, packageName, constraint, line, start, end, start, end));
  }

  /** True for requirements the platform provides, which Packagist does not know about. */
  private static boolean isPlatformRequirement(String packageName) {
    return packageName.startsWith("ext-")
        || packageName.startsWith("lib-")
        || packageName.startsWith("php-")
        || "composer-plugin-api".equals(packageName)
        || "composer-runtime-api".equals(packageName)
        || "php".equals(packageName);
  }
}
