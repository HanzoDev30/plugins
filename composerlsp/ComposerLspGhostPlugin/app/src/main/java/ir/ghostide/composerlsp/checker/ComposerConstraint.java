package ir.ghostide.composerlsp.checker;

import java.util.Locale;

/**
 * Minimal Composer constraint evaluator, used to decide whether a dependency really needs an update
 * or whether the published release is already covered by the declared constraint.
 *
 * <p>Supported forms are the ones that actually show up in a {@code composer.json}: {@code ^1.2},
 * {@code ~1.2}, {@code ~1.2.3}, {@code >=1.0 <2.0}, {@code 1.2.*}, an exact {@code 1.2.3}, {@code
 * *}, and {@code ||} alternatives. Anything referencing a branch, an alias or a fork ({@code
 * dev-main}, {@code 1.0 as 2.0}, {@code my/branch}) is deliberately reported as "no update" — those
 * constraints cannot be judged offline, and a wrong highlight is worse than none.
 */
public final class ComposerConstraint {

  private ComposerConstraint() {}

  /**
   * Returns true when {@code version} is <em>not</em> allowed by {@code constraint}, i.e. the
   * manifest should be updated to reach the published release.
   */
  public static boolean isUpdateAvailable(String constraint, String version) {
    if (constraint == null || version == null) {
      return false;
    }
    String trimmed = constraint.trim();
    if (trimmed.isEmpty() || "*".equals(trimmed)) {
      return false;
    }
    if (!isComparable(version)) {
      return false;
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.contains("dev-") || lower.contains("@") || lower.contains(" as ")) {
      return false;
    }
    for (String branch : trimmed.split("\\|\\|")) {
      if (branchSatisfied(branch.trim(), version)) {
        return false;
      }
    }
    return true;
  }

  /**
   * True when the string is a plain release version such as {@code 1}, {@code 1.2} or {@code
   * 1.2.3}.
   */
  public static boolean isComparable(String version) {
    if (version == null || version.isEmpty()) {
      return false;
    }
    for (int i = 0; i < version.length(); i++) {
      char c = version.charAt(i);
      if ((c >= '0' && c <= '9') || c == '.') {
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * Rewrites a declared constraint so it accepts {@code newest} while keeping the operator the
   * author used: {@code ^1.0} becomes {@code ^2.0}, {@code ~1.0} becomes {@code ~2.0} and an exact
   * or wildcard pin is replaced by the bare version.
   */
  public static String bump(String constraint, String newest) {
    if (constraint == null) {
      return newest;
    }
    String trimmed = constraint.trim();
    if (trimmed.startsWith("~>") || trimmed.startsWith("^")) {
      return "^" + newest;
    }
    if (trimmed.startsWith("~")) {
      return "~" + newest;
    }
    return newest;
  }

  /** A branch is one {@code ||} alternative; every one of its tokens must accept the version. */
  private static boolean branchSatisfied(String branch, String version) {
    if (branch.isEmpty()) {
      return true;
    }
    for (String token : branch.split("[\\s,]+")) {
      if (token.isEmpty()) {
        continue;
      }
      if (!tokenMatches(token, version)) {
        return false;
      }
    }
    return true;
  }

  private static boolean tokenMatches(String token, String version) {
    if ("*".equals(token) || "x".equalsIgnoreCase(token)) {
      return true;
    }
    if (token.startsWith("^")) {
      return caretMatches(token.substring(1), version);
    }
    if (token.startsWith("~>")) {
      return caretMatches(token.substring(2), version);
    }
    if (token.startsWith("~")) {
      return tildeMatches(token.substring(1), version);
    }
    if (token.startsWith(">=") || token.startsWith("<=") || token.startsWith("!=")) {
      int compared = compare(version, token.substring(2));
      if (token.startsWith(">=")) {
        return compared >= 0;
      }
      return token.startsWith("<=") ? compared <= 0 : compared != 0;
    }
    if (token.startsWith("==") || token.startsWith("=")) {
      return compare(version, token.substring(token.startsWith("==") ? 2 : 1)) == 0;
    }
    if (token.startsWith(">")) {
      return compare(version, token.substring(1)) > 0;
    }
    if (token.startsWith("<")) {
      return compare(version, token.substring(1)) < 0;
    }
    if (token.endsWith(".*")) {
      String prefix = token.substring(0, token.length() - 2);
      if (!isComparable(prefix)) {
        return true;
      }
      return version.equals(prefix) || version.startsWith(prefix + ".");
    }
    if (!isComparable(token)) {
      // Unknown shape (a branch, an alias, a path): stay quiet instead of nagging.
      return true;
    }
    return compare(version, token) == 0;
  }

  /** {@code ^1.2} means {@code >=1.2 <2.0.0}; on 0.x the minor is pinned instead of the major. */
  private static boolean caretMatches(String base, String version) {
    if (!isComparable(base)) {
      return true;
    }
    if (compare(version, base) < 0) {
      return false;
    }
    String[] parts = base.split("\\.");
    int major = part(parts, 0);
    int minor = part(parts, 1);
    String upper;
    if (major > 0 || parts.length <= 1) {
      upper = (major + 1) + ".0.0";
    } else if (minor > 0 || parts.length == 2) {
      upper = "0." + (minor + 1) + ".0";
    } else {
      upper = "0.0." + (part(parts, 2) + 1);
    }
    return compare(version, upper) < 0;
  }

  /** {@code ~1.2} means {@code >=1.2 <2.0.0}, {@code ~1.2.3} means {@code >=1.2.3 <1.3.0}. */
  private static boolean tildeMatches(String base, String version) {
    if (!isComparable(base)) {
      return true;
    }
    if (compare(version, base) < 0) {
      return false;
    }
    String[] parts = base.split("\\.");
    int major = part(parts, 0);
    int minor = part(parts, 1);
    String upper = parts.length >= 3 ? major + "." + (minor + 1) + ".0" : (major + 1) + ".0.0";
    return compare(version, upper) < 0;
  }

  private static int part(String[] parts, int index) {
    if (index >= parts.length) {
      return 0;
    }
    try {
      return Integer.parseInt(parts[index].trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Numeric, zero-padded, segment-by-segment comparison; a missing segment counts as zero. */
  private static int compare(String left, String right) {
    String[] a = left.split("[.\\-+]");
    String[] b = right.split("[.\\-+]");
    int length = Math.max(a.length, b.length);
    for (int i = 0; i < length; i++) {
      int x = segment(a, i);
      int y = segment(b, i);
      if (x != y) {
        return x < y ? -1 : 1;
      }
    }
    return 0;
  }

  private static int segment(String[] parts, int index) {
    if (index >= parts.length) {
      return 0;
    }
    try {
      return Integer.parseInt(parts[index].trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
