package ir.hanzodev1375.javaicons;

import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;
import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;

/**
 * A color inlay hint with a plugin-local type name, so it never collides with the renderer the
 * host app registers for {@code ColorInlayHint.TYPE_NAME}.
 *
 * @see ColorInlayHintRendererCompat
 */
public final class ColorInlayHintCompat extends InlayHint {

  public static final String TYPE_NAME = "ghost.rescolor";

  private final ResolvableColor color;

  public ColorInlayHintCompat(int line, int col, ResolvableColor color) {
    super(line, col, TYPE_NAME, CharacterSide.LEFT);
    this.color = color;
  }

  public ResolvableColor getColor() {
    return color;
  }
}