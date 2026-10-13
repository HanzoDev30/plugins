package ir.hanzodev1375.jadx;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** A small "&lt;/&gt;" glyph for the file manager toolbar. Drawn in code, so no resources needed. */
final class CodeIconDrawable extends Drawable {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();

  CodeIconDrawable(int color) {
    paint.setColor(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setStrokeWidth(2f);

    path.moveTo(9f, 7f);
    path.lineTo(4f, 12f);
    path.lineTo(9f, 17f);

    path.moveTo(15f, 7f);
    path.lineTo(20f, 12f);
    path.lineTo(15f, 17f);

    path.moveTo(13.5f, 5f);
    path.lineTo(10.5f, 19f);
  }

  @Override
  public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds.isEmpty()) {
      return;
    }
    float scale = Math.min(bounds.width(), bounds.height()) / 24f;
    int save = canvas.save();
    canvas.translate(
        bounds.left + (bounds.width() - 24f * scale) / 2f,
        bounds.top + (bounds.height() - 24f * scale) / 2f);
    canvas.scale(scale, scale);
    canvas.drawPath(path, paint);
    canvas.restoreToCount(save);
  }

  @Override
  public int getIntrinsicWidth() {
    return 96;
  }

  @Override
  public int getIntrinsicHeight() {
    return 96;
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
