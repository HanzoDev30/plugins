package ir.hanzodev1375.bootstrap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import io.github.rosemoe.sora.graphics.InlayHintRenderParams;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.graphics.inlayHint.InlayHintRenderer;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** Draws the cached Bootstrap logo, preserving its aspect ratio, after the class name. */
public final class BootstrapIconRenderer extends InlayHintRenderer {

  private final Bitmap icon;
  private final Paint localPaint = new Paint();
  private final RectF dst = new RectF();

  public BootstrapIconRenderer(Bitmap icon) {
    this.icon = icon;
    localPaint.setAntiAlias(true);
    localPaint.setFilterBitmap(true);
  }

  @Override
  public String getTypeName() {
    return BootstrapIconHint.TYPE;
  }

  @Override
  public float onMeasure(InlayHint hint, Paint paint, InlayHintRenderParams params) {
    if (!(hint instanceof BootstrapIconHint) || icon == null) return 0f;
    return paint.getSpaceWidth() + params.getTextHeight() * 0.9f;
  }

  @Override
  public void onRender(
      InlayHint hint,
      Canvas canvas,
      Paint paint,
      InlayHintRenderParams params,
      EditorColorScheme colorScheme,
      float measuredWidth) {
    if (!(hint instanceof BootstrapIconHint) || icon == null) return;
    float bw = icon.getWidth();
    float bh = icon.getHeight();
    if (bw <= 0 || bh <= 0) return;

    float maxSize = params.getTextHeight() * 0.9f;
    float scale = Math.min(maxSize / bw, maxSize / bh);
    float w = bw * scale;
    float h = bh * scale;
    float cx = (measuredWidth - w) / 2f;
    float cy = (params.getTextTop() + params.getTextBottom()) / 2f;
    dst.set(cx, cy - h / 2f, cx + w, cy + h / 2f);
    canvas.drawBitmap(icon, null, dst, localPaint);
  }
}
