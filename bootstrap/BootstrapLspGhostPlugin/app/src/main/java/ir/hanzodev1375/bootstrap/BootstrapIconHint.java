package ir.hanzodev1375.bootstrap;

import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;

/** An inlay hint that renders the Bootstrap logo right after a recognised Bootstrap class name. */
public final class BootstrapIconHint extends InlayHint {

  public static final String TYPE = "ghost.bootstrapicon";

  public BootstrapIconHint(int line, int col) {
    super(line, col, TYPE, CharacterSide.LEFT);
  }
}
