package ir.hanzodev1375.autorenametag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class TagDocument {

  private static final Set<String> VOID_ELEMENTS = new HashSet<>();
  private static final Set<String> RAW_TEXT_ELEMENTS = new HashSet<>();

  static {
    Collections.addAll(
        VOID_ELEMENTS,
        "area", "base", "basefont", "bgsound", "br", "col", "command", "embed", "frame", "hr",
        "img", "input", "keygen", "link", "menuitem", "meta", "param", "source", "track", "wbr");
    Collections.addAll(RAW_TEXT_ELEMENTS, "script", "style", "textarea", "title", "plaintext", "xmp");
  }

  private TagDocument() {}

  static final class Token {
    final int start;
    final int nameStart;
    final int nameEnd;
    final boolean closing;

    Token(int start, int nameStart, int nameEnd, boolean closing) {
      this.start = start;
      this.nameStart = nameStart;
      this.nameEnd = nameEnd;
      this.closing = closing;
    }

    String name(String text) {
      return text.substring(nameStart, nameEnd);
    }
  }

  static boolean isNameStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  static boolean isNameChar(char c) {
    return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':';
  }

  /**
   * Finds the tag-name token that contains the caret. {@code index} is a caret position (between
   * characters), so the character at {@code index - 1} is the one just left of the caret.
   */
  static Token locate(String text, int index) {
    int length = text.length();
    if (index <= 0 || index > length) {
      return null;
    }
    int runStart = index;
    while (runStart > 0 && isNameChar(text.charAt(runStart - 1))) {
      runStart--;
    }
    int open = runStart - 1;
    boolean closing = false;
    if (open >= 0 && text.charAt(open) == '/') {
      closing = true;
      open--;
    }
    if (open < 0 || text.charAt(open) != '<') {
      return null;
    }
    int nameStart = closing ? open + 2 : open + 1;
    int nameEnd = nameStart;
    while (nameEnd < length && isNameChar(text.charAt(nameEnd))) {
      nameEnd++;
    }
    if (nameEnd == nameStart) {
      // empty name is only valid for "<|>" and "</|>" (e.g. right after the name was deleted)
      if (nameEnd >= length || text.charAt(nameEnd) != '>') {
        return null;
      }
    } else if (!isNameStart(text.charAt(nameStart))) {
      return null;
    }
    if (index < nameStart || index > nameEnd) {
      return null;
    }
    return new Token(open, nameStart, nameEnd, closing);
  }

  static final class Rename {
    final int start;
    final int end;
    final String name;

    Rename(int start, int end, String name) {
      this.start = start;
      this.end = end;
      this.name = name;
    }
  }

  /**
   * Plans the linked edit for a change that was just applied to {@code text}.
   *
   * <p>The partner tag cannot be found in the current text because the edited tag no longer has the
   * same name as its partner. So the edit is reverted first, the pair is resolved in that old text,
   * and the partner range is mapped back into the current text.
   *
   * @param insert true for an insertion (range [start, end) is the inserted text), false for a
   *     deletion (start is where {@code deleted} used to be)
   */
  static Rename plan(String text, boolean insert, int start, int end, String deleted) {
    int length = text.length();
    int caret;
    String oldText;
    int removedLength = 0;
    int insertedLength = 0;
    if (insert) {
      if (start < 0 || end < start || end > length) {
        return null;
      }
      insertedLength = end - start;
      oldText = text.substring(0, start) + text.substring(end);
      caret = end;
    } else {
      if (start < 0 || start > length || deleted == null) {
        return null;
      }
      removedLength = deleted.length();
      oldText = text.substring(0, start) + deleted + text.substring(start);
      caret = start;
    }
    Token current = locate(text, caret);
    Token before = locate(oldText, start);
    if (current == null || before == null || current.start != before.start) {
      return null;
    }
    Token partner = findPartner(oldText, before);
    if (partner == null) {
      return null;
    }
    int shift;
    if (partner.nameEnd <= start) {
      shift = 0;
    } else if (insert && partner.nameStart >= start) {
      shift = insertedLength;
    } else if (!insert && partner.nameStart >= start + removedLength) {
      shift = -removedLength;
    } else {
      return null;
    }
    int from = partner.nameStart + shift;
    int to = partner.nameEnd + shift;
    if (from < 0 || to < from || to > length) {
      return null;
    }
    String oldPartnerName = oldText.substring(partner.nameStart, partner.nameEnd);
    if (!text.substring(from, to).equals(oldPartnerName)) {
      return null;
    }
    String name = current.name(text);
    if (name.equals(oldPartnerName)) {
      return null;
    }
    return new Rename(from, to, name);
  }

  static Token findPartner(String text, Token target) {
    int length = text.length();
    int[] stack = new int[96];
    int size = 0;
    int position = 0;
    while (position < length) {
      int open = text.indexOf('<', position);
      if (open < 0 || open + 1 >= length) {
        return null;
      }
      char bracket = text.charAt(open + 1);
      if (bracket == '!') {
        if (regionMatches(text, open + 1, "!--")) {
          position = skipPast(text, open + 4, "-->");
        } else if (regionMatches(text, open + 1, "[cdata[")) {
          position = skipPast(text, open + 8, "]]>");
        } else {
          position = skipPastTag(text, open + 1);
        }
        continue;
      }
      if (bracket == '?') {
        position = skipPastTag(text, open + 1);
        continue;
      }
      boolean closing = bracket == '/';
      int nameStart = closing ? open + 2 : open + 1;
      if (nameStart >= length) {
        return null;
      }
      int nameEnd;
      if (text.charAt(nameStart) == '>') {
        nameEnd = nameStart; // "<>" / "</>" with an empty name
      } else if (isNameStart(text.charAt(nameStart))) {
        nameEnd = nameStart + 1;
        while (nameEnd < length && isNameChar(text.charAt(nameEnd))) {
          nameEnd++;
        }
      } else {
        position = open + 1;
        continue;
      }
      int tagEnd = skipPastTag(text, nameEnd);
      if (closing) {
        int match = findMatch(text, stack, size, nameStart, nameEnd);
        if (match < 0) {
          position = tagEnd + 1;
          continue;
        }
        if (target.closing && target.start == open) {
          return new Token(stack[match * 3], stack[match * 3 + 1], stack[match * 3 + 2], false);
        }
        if (!target.closing && target.start == stack[match * 3]) {
          return new Token(open, nameStart, nameEnd, true);
        }
        size = match;
        position = tagEnd + 1;
        continue;
      }
      boolean selfClosing = tagEnd > nameEnd && text.charAt(tagEnd - 1) == '/';
      boolean raw = !selfClosing && isElement(text, nameStart, nameEnd, RAW_TEXT_ELEMENTS);
      boolean voided = !selfClosing && isElement(text, nameStart, nameEnd, VOID_ELEMENTS);
      if (!selfClosing && !voided) {
        if (size * 3 + 3 > stack.length) {
          int[] grown = new int[stack.length * 2];
          System.arraycopy(stack, 0, grown, 0, stack.length);
          stack = grown;
        }
        stack[size * 3] = open;
        stack[size * 3 + 1] = nameStart;
        stack[size * 3 + 2] = nameEnd;
        size++;
        if (raw) {
          int closingAt = findRawTextClose(text, tagEnd + 1, nameStart, nameEnd);
          if (closingAt < 0) {
            return null;
          }
          position = closingAt;
          continue;
        }
      }
      position = tagEnd + 1;
    }
    return null;
  }

  private static int findMatch(String text, int[] stack, int size, int nameStart, int nameEnd) {
    for (int i = size - 1; i >= 0; i--) {
      if (sameName(text, stack[i * 3 + 1], stack[i * 3 + 2], nameStart, nameEnd)) {
        return i;
      }
    }
    return -1;
  }

  private static int findRawTextClose(String text, int from, int nameStart, int nameEnd) {
    int position = from;
    while (position < text.length()) {
      int close = text.indexOf("</", position);
      if (close < 0) {
        return -1;
      }
      int closeNameStart = close + 2;
      if (closeNameStart < text.length() && isNameStart(text.charAt(closeNameStart))) {
        int closeNameEnd = closeNameStart + 1;
        while (closeNameEnd < text.length() && isNameChar(text.charAt(closeNameEnd))) {
          closeNameEnd++;
        }
        if (sameName(text, nameStart, nameEnd, closeNameStart, closeNameEnd)) {
          return close;
        }
      }
      position = close + 2;
    }
    return -1;
  }

  static boolean sameName(String text, int startA, int endA, int startB, int endB) {
    if (endA - startA != endB - startB) {
      return false;
    }
    for (int i = 0; i < endA - startA; i++) {
      if (Character.toLowerCase(text.charAt(startA + i)) != Character.toLowerCase(text.charAt(startB + i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isElement(String text, int start, int end, Set<String> names) {
    if (end - start > 12) {
      return false;
    }
    StringBuilder builder = new StringBuilder(end - start);
    for (int i = start; i < end; i++) {
      builder.append(Character.toLowerCase(text.charAt(i)));
    }
    return names.contains(builder.toString());
  }

  private static boolean regionMatches(String text, int offset, String needle) {
    int length = needle.length();
    if (offset + length > text.length()) {
      return false;
    }
    String lowerNeedle = needle.toLowerCase(Locale.ROOT);
    for (int i = 0; i < length; i++) {
      if (Character.toLowerCase(text.charAt(offset + i)) != lowerNeedle.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static int skipPast(String text, int from, String needle) {
    int at = text.indexOf(needle, from);
    return at < 0 ? text.length() : at + needle.length();
  }

  private static int skipPastTag(String text, int from) {
    char quote = 0;
    for (int i = from; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    int at = text.indexOf('>', from);
    return at < 0 ? text.length() : at;
  }
}