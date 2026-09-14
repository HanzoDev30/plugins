package ir.ghostides.thememaker.palette;

/**
 * Named colors extracted from an image. {@code background}/{@code text} are chosen first so the
 * theme is always readable, {@code accent} is the most colorful colour in the picture and {@code
 * syntax} carries the remaining distinct colours that map to syntax highlighting / bracket pairs.
 */
public final class Palette {

  public final int background;
  public final int surface;
  public final int stroke;
  public final int text;
  public final int muted;
  public final int accent;
  public final int[] syntax;

  public Palette(
      int background, int surface, int stroke, int text, int muted, int accent, int[] syntax) {
    this.background = background;
    this.surface = surface;
    this.stroke = stroke;
    this.text = text;
    this.muted = muted;
    this.accent = accent;
    this.syntax = syntax;
  }

  /** All colors, in a stable order, for the preview swatches. */
  public int[] allColors() {
    int[] all = new int[6 + syntax.length];
    all[0] = background;
    all[1] = surface;
    all[2] = stroke;
    all[3] = text;
    all[4] = muted;
    all[5] = accent;
    System.arraycopy(syntax, 0, all, 6, syntax.length);
    return all;
  }
}
