package ir.ghostide.tailwindcsslsp;

import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;

/** An inlay hint that renders the Tailwind logo right after a recognised Tailwind class name. */
public final class TailwindIconHint extends InlayHint {

  public static final String TYPE = "ghost.tailwindicon";

  public TailwindIconHint(int line, int col) {
    super(line, col, TYPE, CharacterSide.LEFT);
  }
}
