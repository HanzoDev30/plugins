package ir.hanzodev1375.jadx;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import android.graphics.Typeface;

import ir.theme.M3Theme;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A web-free Java syntax highlighter. Walks the source once and paints keywords, types,
 * strings, chars, numbers, annotations and comments with colors taken from {@link M3Theme},
 * so the decompiled code follows the active IDE theme.
 */
final class JavaHighlighter {

  private static final Set<String> KEYWORDS =
      new HashSet<>(
          Arrays.asList(
              "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
              "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
              "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
              "interface", "long", "native", "new", "package", "private", "protected", "public",
              "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
              "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
              "null", "var", "record", "sealed", "permits", "yield"));

  private final int keywordColor;
  private final int typeColor;
  private final int stringColor;
  private final int commentColor;
  private final int numberColor;

  private JavaHighlighter(
      int keywordColor, int typeColor, int stringColor, int commentColor, int numberColor) {
    this.keywordColor = keywordColor;
    this.typeColor = typeColor;
    this.stringColor = stringColor;
    this.commentColor = commentColor;
    this.numberColor = numberColor;
  }

  /** Builds a highlighter from the current M3 theme (falls back to sensible dark defaults). */
  static JavaHighlighter fromTheme() {
    return new JavaHighlighter(
        or(M3Theme.primary(), 0xFFD0BCFF),
        or(M3Theme.tertiary(), 0xFFEFB8C8),
        or(M3Theme.secondary(), 0xFFB7E8C8),
        or(M3Theme.outline(), 0xFF7A7A7A),
        or(M3Theme.secondary(), 0xFFE8C07A));
  }

  private static int or(Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  /** Returns a colored, bold-keyword copy of {@code src}. Untouched text keeps the view color. */
  CharSequence highlight(CharSequence src) {
    if (src == null || src.length() == 0) {
      return src;
    }
    SpannableStringBuilder sb = new SpannableStringBuilder(src);
    int n = sb.length();
    int i = 0;
    while (i < n) {
      char c = sb.charAt(i);

      if (c == '/' && i + 1 < n && sb.charAt(i + 1) == '/') {
        int end = i + 2;
        while (end < n && sb.charAt(end) != '\n') {
          end++;
        }
        span(sb, i, end, commentColor, false);
        i = end;
      } else if (c == '/' && i + 1 < n && sb.charAt(i + 1) == '*') {
        int end = i + 2;
        while (end + 1 < n && !(sb.charAt(end) == '*' && sb.charAt(end + 1) == '/')) {
          end++;
        }
        end = Math.min(n, end + 2);
        span(sb, i, end, commentColor, false);
        i = end;
      } else if (c == '"' || c == '\'') {
        int end = scanLiteral(sb, i, c, n);
        span(sb, i, end, stringColor, false);
        i = end;
      } else if (c == '@' && i + 1 < n && isIdentStart(sb.charAt(i + 1))) {
        int end = i + 1;
        while (end < n && isIdentPart(sb.charAt(end))) {
          end++;
        }
        span(sb, i, end, typeColor, false);
        i = end;
      } else if (isIdentStart(c)) {
        int end = i;
        while (end < n && isIdentPart(sb.charAt(end))) {
          end++;
        }
        String word = sb.subSequence(i, end).toString();
        if (KEYWORDS.contains(word)) {
          span(sb, i, end, keywordColor, true);
        } else if (Character.isUpperCase(c)) {
          span(sb, i, end, typeColor, false);
        }
        i = end;
      } else if (Character.isDigit(c)) {
        int end = i;
        while (end < n && (isIdentPart(sb.charAt(end)) || sb.charAt(end) == '.')) {
          end++;
        }
        span(sb, i, end, numberColor, false);
        i = end;
      } else {
        i++;
      }
    }
    return sb;
  }

  private static int scanLiteral(SpannableStringBuilder sb, int start, char quote, int n) {
    int end = start + 1;
    while (end < n) {
      char d = sb.charAt(end);
      if (d == '\\' && end + 1 < n) {
        end += 2;
        continue;
      }
      end++;
      if (d == quote || d == '\n') {
        break;
      }
    }
    return Math.min(end, n);
  }

  private static void span(
      SpannableStringBuilder sb, int start, int end, int color, boolean bold) {
    if (start >= end) {
      return;
    }
    sb.setSpan(
        new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (bold) {
      sb.setSpan(
          new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private static boolean isIdentStart(char c) {
    return Character.isLetter(c) || c == '_' || c == '$';
  }

  private static boolean isIdentPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }
}
