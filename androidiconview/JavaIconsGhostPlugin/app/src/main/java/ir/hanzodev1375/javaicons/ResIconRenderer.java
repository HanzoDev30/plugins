package ir.hanzodev1375.javaicons;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Log;

import io.github.rosemoe.sora.graphics.InlayHintRenderParams;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.graphics.inlayHint.InlayHintRenderer;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

public final class ResIconRenderer extends InlayHintRenderer {

  private static final String TAG = "gpl:ir.hanzodev1375.javaicons";

  private final ResIcons icons;
  private final Paint localPaint = new Paint();
  private final RectF dst = new RectF();

  private int measureCalls;
  private int renderCalls;

  public ResIconRenderer(ResIcons icons) {
    this.icons = icons;
    localPaint.setAntiAlias(true);
    localPaint.setFilterBitmap(true);
  }

  @Override
  public String getTypeName() {
    return ResIconHint.TYPE;
  }

  @Override
  public float onMeasure(InlayHint hint, Paint paint, InlayHintRenderParams params) {
    if (!(hint instanceof ResIconHint)) {
      Log.d(TAG, "onMeasure: not ResIconHint, type=" + hint.getClass().getName());
      return 0f;
    }
    float space = paint.getSpaceWidth();
    float textH = params.getTextHeight();
    float w = space + textH * 0.9f;
    if (measureCalls++ < 5) {
      Log.d(TAG, "onMeasure: space=" + space + " textH=" + textH + " w=" + w);
    }
    return w;
  }

  @Override
  public void onRender(
      InlayHint hint,
      Canvas canvas,
      Paint paint,
      InlayHintRenderParams params,
      EditorColorScheme colorScheme,
      float measuredWidth) {
    if (!(hint instanceof ResIconHint)) return;
    ResIconHint h = (ResIconHint) hint;
    Bitmap bmp = icons.get(h.key);
    if (renderCalls++ < 5) {
      Log.d(TAG, "onRender: line=" + h.getLine() + " col=" + h.getColumn()
          + " key=" + h.key + " bmp=" + (bmp != null)
          + " measuredWidth=" + measuredWidth
          + " top=" + params.getTextTop() + " bottom=" + params.getTextBottom());
    }
    if (bmp == null) {
      Log.d(TAG, "onRender: bmp null for " + h.key);
      return;
    }
    float size = params.getTextHeight() * 0.9f;
    float left = Math.max(0f, (measuredWidth - size) / 2f);
    float cy = (params.getTextTop() + params.getTextBottom()) / 2f;
    dst.set(left, cy - size / 2f, left + size, cy + size / 2f);
    canvas.drawBitmap(bmp, null, dst, localPaint);
    if (renderCalls < 5) {
      Log.d(TAG, "onRender: drew at " + dst);
    }
  }
}