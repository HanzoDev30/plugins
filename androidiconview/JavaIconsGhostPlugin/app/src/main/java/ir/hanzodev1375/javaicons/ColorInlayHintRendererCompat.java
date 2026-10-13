package ir.hanzodev1375.javaicons;

import android.graphics.Canvas;
import android.graphics.Color;

import io.github.rosemoe.sora.graphics.InlayHintRenderParams;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.graphics.inlayHint.InlayHintRenderer;
import io.github.rosemoe.sora.lang.styling.color.ConstColor;
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * A color inlay renderer that draws a small filled square (with a border) at the hint anchor.
 *
 * <p>Java port of Sora's {@code ColorInlayHintRenderer} but typed to {@link ColorInlayHintCompat},
 * so it answers for the plugin-local type name instead of {@code ColorInlayHint.TYPE_NAME}.
 *
 * @see ColorInlayHintCompat
 * @author Rosemoe
 */
public class ColorInlayHintRendererCompat extends InlayHintRenderer {

  private final ResolvableColor borderColor;
  protected final Paint localPaint = new Paint();

  public ColorInlayHintRendererCompat() {
    this(new ConstColor(Color.WHITE));
  }

  public ColorInlayHintRendererCompat(ResolvableColor borderColor) {
    this.borderColor = borderColor;
    localPaint.setAntiAlias(true);
    localPaint.setStrokeWidth(2f);
  }

  @Override
  public String getTypeName() {
    return ColorInlayHintCompat.TYPE_NAME;
  }

  @Override
  public float onMeasure(InlayHint inlayHint, Paint paint, InlayHintRenderParams params) {
    float margin = paint.getSpaceWidth();
    return margin + params.getTextHeight() * 0.75f;
  }

  @Override
  public void onRender(
      InlayHint inlayHint,
      Canvas canvas,
      Paint paint,
      InlayHintRenderParams params,
      EditorColorScheme colorScheme,
      float measuredWidth) {
    float centerX = measuredWidth / 2f;
    float centerY = (params.getTextTop() + params.getTextBottom()) / 2f;
    float halfSize = params.getTextHeight() * 0.75f / 2f;
    float corner = halfSize * 0.25f;

    int color = Color.WHITE;
    if (inlayHint instanceof ColorInlayHintCompat
        && ((ColorInlayHintCompat) inlayHint).getColor() != null) {
      color = ((ColorInlayHintCompat) inlayHint).getColor().resolve(colorScheme);
    }

    localPaint.setColor(color);
    localPaint.setStyle(android.graphics.Paint.Style.FILL);
    canvas.drawRoundRect(
        centerX - halfSize,
        centerY - halfSize,
        centerX + halfSize,
        centerY + halfSize,
        corner,
        corner,
        localPaint);

    localPaint.setColor(borderColor.resolve(colorScheme));
    localPaint.setStyle(android.graphics.Paint.Style.STROKE);
    canvas.drawRoundRect(
        centerX - halfSize,
        centerY - halfSize,
        centerX + halfSize,
        centerY + halfSize,
        corner,
        corner,
        localPaint);
  }
}