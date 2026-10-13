package ir.hanzodev1375.javaicons;

import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;

public class TextInlayHintCompat extends InlayHint {
  private final String text;

  public TextInlayHintCompat(int line, int col, String d) {
    super(line, col, TextInlayHint.TYPE_NAME, CharacterSide.LEFT);
    this.text = d;
  }

  public String getText() {
    return text;
  }
}
