package ir.hanzodev1375.htmlicons;

import io.github.rosemoe.sora.lang.styling.inlayHint.CharacterSide;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;

/** An inlay hint that renders a local image file preview right after the matched reference. */
public final class HtmlIconHint extends InlayHint {

  public static final String TYPE = "ghost.htmlicon";

  public final String path;

  public HtmlIconHint(int line, int col, String path) {
    super(line, col, TYPE, CharacterSide.LEFT);
    this.path = path;
  }
}