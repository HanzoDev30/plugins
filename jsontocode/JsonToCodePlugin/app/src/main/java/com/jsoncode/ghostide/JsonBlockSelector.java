package com.jsoncode.ghostide;

import io.github.rosemoe.sora.text.Content;

/**
 * Locates the innermost JSON object or array that encloses a character position.
 *
 * <p>Sora's built-in long press only selects a single word (see {@code EditorTouchEventHandler
 * .onLongPress}, which calls {@code selectWord} right after dispatching the event). A lone word is
 * never usable JSON, so the plugin resolves the enclosing block itself.
 */
final class JsonBlockSelector {

  private JsonBlockSelector() {}

  /**
   * Returns a two element array holding the inclusive {@code [start, end]} character range of the
   * innermost JSON object or array containing {@code index}, or {@code null} when the position is
   * not inside one.
   */
  static int[] findEnclosingBlock(Content text, int index) {
    if (text == null) {
      return null;
    }
    int length = text.length();
    if (length == 0) {
      return null;
    }

    int target = index;
    if (target < 0) {
      target = 0;
    }
    if (target > length - 1) {
      target = length - 1;
    }

    int[] stack = new int[32];
    int depth = 0;
    int bestStart = -1;
    int bestEnd = -1;
    boolean inString = false;

    for (int position = 0; position < length; position++) {
      char c = text.charAt(position);

      if (inString) {
        if (c == '\\') {
          position++;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == '{' || c == '[') {
        if (depth == stack.length) {
          int[] grown = new int[stack.length * 2];
          System.arraycopy(stack, 0, grown, 0, stack.length);
          stack = grown;
        }
        stack[depth++] = position;
        continue;
      }

      if (c == '}' || c == ']') {
        if (depth == 0) {
          continue;
        }
        int start = stack[--depth];
        // Blocks close innermost first, so the first hit is the innermost enclosing block.
        if (bestStart < 0 && start <= target && target <= position) {
          bestStart = start;
          bestEnd = position;
        }
      }
    }

    if (bestStart < 0) {
      return recoverUnterminated(stack, depth, target, length - 1);
    }
    return new int[] {bestStart, bestEnd};
  }

  /** Handles truncated documents where the closing bracket was never typed yet. */
  private static int[] recoverUnterminated(int[] stack, int depth, int target, int lastIndex) {
    for (int i = depth - 1; i >= 0; i--) {
      if (stack[i] <= target) {
        return new int[] {stack[i], lastIndex};
      }
    }
    return null;
  }
}
