package ir.ghostides.thememaker.palette;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts a usable {@link Palette} from a {@link Bitmap}.
 *
 * <p>Algorithm: the bitmap is down-scaled, every opaque pixel is quantized into a coarse colour
 * bucket (5 bits / channel) and counted; the most frequent buckets are then de-duplicated with a
 * minimum distance so the result is a handful of genuinely distinct colours. Roles (background,
 * text, accent, …) are assigned afterwards from luminance / chroma so the produced theme stays
 * readable for both dark and light pictures.
 */
public final class PaletteExtractor {

  private static final int MAX_SAMPLE_SIZE = 96;
  private static final int MAX_COLORS = 10;

  private PaletteExtractor() {}

  public static Palette extract(Bitmap bitmap) {
    Bitmap scaled = scale(bitmap, MAX_SAMPLE_SIZE);
    int w = scaled.getWidth();
    int h = scaled.getHeight();

    // quantize into 5 bits per channel buckets and accumulate their pixel sums
    Map<Integer, Bucket> buckets = new HashMap<>();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int c = scaled.getPixel(x, y);
        if (Colors.alpha(c) < 32) {
          continue;
        }
        int key = (Colors.red(c) >> 3) << 10 | (Colors.green(c) >> 3) << 5 | (Colors.blue(c) >> 3);
        Bucket b = buckets.get(key);
        if (b == null) {
          b = new Bucket();
          buckets.put(key, b);
        }
        b.count++;
        b.r += Colors.red(c);
        b.g += Colors.green(c);
        b.b += Colors.blue(c);
      }
    }

    if (buckets.isEmpty()) {
      return defaultPalette();
    }

    List<Bucket> sorted = new ArrayList<>(buckets.values());
    sorted.sort((a, b2) -> Integer.compare(b2.count, a.count));

    // greedy de-dup: keep colors at least `minDist` apart
    List<Integer> distinct = new ArrayList<>();
    float minDist = 0.24f;
    for (Bucket b : sorted) {
      int avg = b.average();
      boolean tooClose = false;
      for (int kept : distinct) {
        if (Colors.distance(avg, kept) < minDist) {
          tooClose = true;
          break;
        }
      }
      if (!tooClose) {
        distinct.add(avg);
        if (distinct.size() >= MAX_COLORS) {
          break;
        }
      }
    }
    if (distinct.size() < 3) {
      // very flat image: loosen the distance threshold and retry
      distinct.clear();
      for (Bucket b : sorted) {
        int avg = b.average();
        boolean tooClose = false;
        for (int kept : distinct) {
          if (Colors.distance(avg, kept) < 0.12f) {
            tooClose = true;
            break;
          }
        }
        if (!tooClose) {
          distinct.add(avg);
        }
      }
    }

    List<Integer> byLuma = new ArrayList<>(distinct);
    byLuma.sort((a, b2) -> Float.compare(Colors.luminance(a), Colors.luminance(b2)));

    int darkest = byLuma.get(0);
    boolean lightPicture = Colors.luminance(byLuma.get(byLuma.size() - 1)) > 0.72f
        && byLuma.size() >= 2 && Colors.luminance(darkest) > 0.45f;
    boolean darkTheme = !lightPicture;

    // background: dominant (darkest) colour, pushed a little darker for a dark theme
    int background = darkTheme ? Colors.darken(darkest, 0.06f) : Colors.lighten(darkest, 0.12f);

    // accent: most chromatic color among the mid-luminance ones
    int accent = distinct.get(0);
    float bestChroma = -1f;
    for (int c : distinct) {
      float lum = Colors.luminance(c);
      if (lum > 0.15f && lum < 0.9f) {
        float ch = Colors.chroma(c);
        if (ch > bestChroma) {
          bestChroma = ch;
          accent = c;
        }
      }
    }

    int text = darkTheme ? Colors.argb(255, 230, 232, 240) : Colors.argb(255, 22, 24, 30);
    int surface = Colors.mix(background, accent, 0.06f);
    int stroke = Colors.mix(text, background, darkTheme ? 0.16f : 0.35f);
    int muted = Colors.mix(text, background, 0.52f);

    // syntax colors: the remaining distinct colors, padded with hue-shifted accents
    List<Integer> syntax = new ArrayList<>();
    for (int c : distinct) {
      if (c != accent && Colors.distance(c, background) > 0.18f) {
        syntax.add(c);
      }
    }
    while (syntax.size() < 6) {
      syntax.add(Colors.hueShift(accent, 60f * syntax.size()));
    }

    return new Palette(
        background,
        surface,
        stroke,
        text,
        muted,
        accent,
        toArray(syntax));
  }

  private static Palette defaultPalette() {
    int background = Colors.argb(255, 24, 26, 34);
    int accent = Colors.argb(255, 97, 175, 239);
    return new Palette(
        background,
        Colors.mix(background, accent, 0.06f),
        Colors.argb(255, 62, 68, 82),
        Colors.argb(255, 230, 232, 240),
        Colors.argb(255, 92, 99, 112),
        accent,
        new int[] {
          Colors.argb(255, 198, 120, 221),
          Colors.argb(255, 86, 182, 194),
          Colors.argb(255, 209, 154, 102),
          Colors.argb(255, 224, 108, 117),
          Colors.argb(255, 97, 175, 239),
          Colors.argb(255, 229, 192, 123)
        });
  }

  private static Bitmap scale(Bitmap src, int maxSize) {
    int w = src.getWidth();
    int h = src.getHeight();
    if (w <= maxSize && h <= maxSize) {
      return src;
    }
    float scale = Math.min((float) maxSize / w, (float) maxSize / h);
    return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), false);
  }

  private static int[] toArray(List<Integer> list) {
    int[] arr = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }

  private static final class Bucket {
    int count;
    long r;
    long g;
    long b;

    int average() {
      return Colors.argb(255, (int) (r / count), (int) (g / count), (int) (b / count));
    }
  }
}
