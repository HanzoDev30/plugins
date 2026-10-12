package ir.hanzodev1375.svgtopng;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileInputStream;

/** Thin helpers around AndroidSVG for loading and rasterising vector files. */
final class SvgRenderer {

  private SvgRenderer() {}

  static SVG load(File file) throws Exception {
    try (FileInputStream in = new FileInputStream(file)) {
      return SVG.getFromInputStream(in);
    }
  }

  /**
   * Intrinsic size of the document in pixels. Prefers the declared {@code width}/{@code height},
   * falls back to the aspect ratio, and finally to a 512x512 square.
   */
  static int[] intrinsic(SVG svg) {
    float width = svg.getDocumentWidth();
    float height = svg.getDocumentHeight();
    float ratio = svg.getDocumentAspectRatio();

    if (width <= 0 && height <= 0) {
      width = 512;
      height = 512;
    } else if (width <= 0) {
      width = ratio > 0 ? height * ratio : height;
    } else if (height <= 0) {
      height = ratio > 0 ? width / ratio : width;
    }

    return new int[] {Math.max(1, Math.round(width)), Math.max(1, Math.round(height))};
  }

  static Bitmap raster(SVG svg, int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Picture picture = svg.renderToPicture(width, height);
    canvas.drawPicture(picture);
    return bitmap;
  }
}
