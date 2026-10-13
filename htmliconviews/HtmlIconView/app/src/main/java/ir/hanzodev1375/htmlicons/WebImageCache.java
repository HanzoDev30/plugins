package ir.hanzodev1375.htmlicons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

/**
 * Decodes raster images with {@link BitmapFactory} and SVG files with AndroidSVG (rendered into a
 * {@link Picture} and drawn to a {@link Bitmap} through {@link PictureDrawable}, i.e. the {@code
 * android.graphics.drawable} package). Bitmaps are cached in a small LRU map, so the renderer only
 * ever reads from memory on the drawing thread.
 */
final class WebImageCache {

  private static final int MAX_ENTRIES = 64;
  private static final int MAX_DIMENSION = 256;

  private final Map<String, Bitmap> cache =
      new LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
          return size() > MAX_ENTRIES;
        }
      };

  /** Warm the cache on a background thread; {@link #get} must not be called concurrently. */
  void preload(File file) {
    Bitmap bitmap = decode(file);
    if (bitmap == null) return;
    cache.put(file.getAbsolutePath(), bitmap);
  }

  Bitmap get(String path) {
    return cache.get(path);
  }

  private static Bitmap decode(File file) {
    String name = file.getName();
    String lower = name.toLowerCase();
    try {
      if (lower.endsWith(".svg")) {
        return decodeSvg(file);
      }
      return decodeRaster(file);
    } catch (Throwable t) {
      return null;
    }
  }

  private static Bitmap decodeRaster(File file) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

    int sample = 1;
    int w = bounds.outWidth;
    int h = bounds.outHeight;
    while (w / (sample * 2) >= MAX_DIMENSION && h / (sample * 2) >= MAX_DIMENSION) {
      sample *= 2;
    }
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = sample;
    return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
  }

  private static Bitmap decodeSvg(File file) {
    if (!file.isFile()) return null;
    try (InputStream in = new FileInputStream(file)) {
      SVG svg = SVG.getFromInputStream(in);
      Picture picture = svg.renderToPicture();
      int w = picture.getWidth();
      int h = picture.getHeight();
      if (w <= 0 || h <= 0) {
        w = 128;
        h = 128;
      }
      float scale = Math.min(1f, MAX_DIMENSION / (float) Math.max(w, h));
      int bw = Math.max(1, Math.round(w * scale));
      int bh = Math.max(1, Math.round(h * scale));

      Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      PictureDrawable drawable = new PictureDrawable(picture);
      drawable.setBounds(0, 0, bw, bh);
      drawable.draw(canvas);
      return bitmap;
    } catch (IOException e) {
      return null;
    } catch (SVGParseException e) {
      return null;
    }
  }

  void clear() {
    cache.clear();
  }
}