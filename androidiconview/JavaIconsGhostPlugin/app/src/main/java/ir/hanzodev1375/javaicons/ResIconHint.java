package ir.hanzodev1375.javaicons;

import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;

public final class ResIconHint extends InlayHint {

  public static final String TYPE = "ghost.resicon";

  public final String key;

  public ResIconHint(int line, int column, String key) {
    super(line, column, TYPE, CharacterSide.LEFT);
    this.key = key;
  }
}