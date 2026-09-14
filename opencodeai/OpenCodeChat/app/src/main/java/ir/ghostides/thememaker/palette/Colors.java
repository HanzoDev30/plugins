package ir.ghostides.thememaker.palette;

/** Small dependency-free color math used to turn a picture into a theme. */
public final class Colors {

  private Colors() {}

  public static int argb(int a, int r, int g, int b) {
    return (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
  }

  public static int alpha(int c) {
    return c >>> 24;
  }

  public static int red(int c) {
    return c >> 16 & 0xff;
  }

  public static int green(int c) {
    return c >> 8 & 0xff;
  }

  public static int blue(int c) {
    return c & 0xff;
  }

  public static int withAlpha(int c, int a) {
    return argb(a, red(c), green(c), blue(c));
  }

  /** {@code "#RRGGBB"} for opaque colors, {@code "#AARRGGBB"} otherwise. */
  public static String hex(int c) {
    if (alpha(c) == 0xff) {
      return String.format("#%02X%02X%02X", red(c), green(c), blue(c));
    }
    return String.format("#%02X%02X%02X%02X", alpha(c), red(c), green(c), blue(c));
  }

  /** Linear mix: t = 0 -> a, t = 1 -> b. */
  public static int mix(int a, int b, float t) {
    if (t <= 0f) {
      return a;
    }
    if (t >= 1f) {
      return b;
    }
    int r = (int) (red(a) + (red(b) - red(a)) * t);
    int g = (int) (green(a) + (green(b) - green(a)) * t);
    int bl = (int) (blue(a) + (blue(b) - blue(a)) * t);
    return argb(alpha(a), r, g, bl);
  }

  public static int lighten(int c, float amount) {
    return argb(
        alpha(c),
        clamp(red(c) + (int) (255 * amount)),
        clamp(green(c) + (int) (255 * amount)),
        clamp(blue(c) + (int) (255 * amount)));
  }

  public static int darken(int c, float amount) {
    return argb(
        alpha(c),
        clamp(red(c) - (int) (255 * amount)),
        clamp(green(c) - (int) (255 * amount)),
        clamp(blue(c) - (int) (255 * amount)));
  }

  public static int clamp(int v) {
    return v < 0 ? 0 : Math.min(v, 255);
  }

  /** Rec. 709 relative luminance in {@code [0, 1]}. */
  public static float luminance(int c) {
    int r = red(c);
    int g = green(c);
    int b = blue(c);
    return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
  }

  /** Saturation-ish measure (chroma) in {@code [0, 1]}. */
  public static float chroma(int c) {
    int max = Math.max(red(c), Math.max(green(c), blue(c)));
    int min = Math.min(red(c), Math.min(green(c), blue(c)));
    return (max - min) / 255f;
  }

  /** Euclidean distance between two colors normalized to {@code [0, 1]}. */
  public static float distance(int a, int b) {
    float dr = (red(a) - red(b)) / 255f;
    float dg = (green(a) - green(b)) / 255f;
    float db = (blue(a) - blue(b)) / 255f;
    return (float) Math.sqrt(dr * dr + dg * dg + db * db);
  }

  /** Rotate the hue of {@code c} by {@code degrees}, keeping saturation/lightness. */
  public static int hueShift(int c, float degrees) {
    float[] hsl = rgbToHsl(red(c), green(c), blue(c));
    hsl[0] = (hsl[0] + degrees) % 360f;
    if (hsl[0] < 0f) {
      hsl[0] += 360f;
    }
    int rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
    return argb(alpha(c), red(rgb), green(rgb), blue(rgb));
  }

  private static float[] rgbToHsl(int r, int g, int b) {
    float rf = r / 255f;
    float gf = g / 255f;
    float bf = b / 255f;
    float max = Math.max(rf, Math.max(gf, bf));
    float min = Math.min(rf, Math.min(gf, bf));
    float l = (max + min) / 2f;
    float s = 0f;
    float h = 0f;
    if (max != min) {
      float d = max - min;
      s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
      if (max == rf) {
        h = (gf - bf) / d + (gf < bf ? 6f : 0f);
      } else if (max == gf) {
        h = (bf - rf) / d + 2f;
      } else {
        h = (rf - gf) / d + 4f;
      }
      h *= 60f;
    }
    return new float[] {h, s, l};
  }

  private static int hslToRgb(float h, float s, float l) {
    float c = (1f - Math.abs(2f * l - 1f)) * s;
    float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
    float m = l - c / 2f;
    float r = 0f;
    float g = 0f;
    float b = 0f;
    if (h < 60f) {
      r = c;
      g = x;
    } else if (h < 120f) {
      r = x;
      g = c;
    } else if (h < 180f) {
      g = c;
      b = x;
    } else if (h < 240f) {
      g = x;
      b = c;
    } else if (h < 300f) {
      r = x;
      b = c;
    } else {
      r = c;
      b = x;
    }
    return argb(255, Math.round((r + m) * 255f), Math.round((g + m) * 255f), Math.round((b + m) * 255f));
  }
}
