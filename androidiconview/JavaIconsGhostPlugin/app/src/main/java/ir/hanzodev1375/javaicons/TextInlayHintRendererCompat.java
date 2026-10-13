package ir.hanzodev1375.javaicons;

import android.graphics.Canvas;

import io.github.rosemoe.sora.graphics.InlayHintRenderParams;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.graphics.inlayHint.InlayHintRenderer;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * A general inlay hint of text. The hint text will be displayed in a round colored rect.
 *
 * @see TextInlayHint
 * @author Rosemoe
 */
public class TextInlayHintRendererCompat extends InlayHintRenderer {

  public static final TextInlayHintRendererCompat DefaultInstance =
      new TextInlayHintRendererCompat();

  protected final Paint localPaint = new Paint();

  public TextInlayHintRendererCompat() {
    localPaint.setAntiAlias(true);
  }

  @Override
  public String getTypeName() {
    return TextInlayHint.TYPE_NAME;
  }

  @Override
  public float onMeasure(InlayHint inlayHint, Paint paint, InlayHintRenderParams params) {
    localPaint.setTypeface(paint.getTypeface());
    localPaint.setTextSize(paint.getTextSize() * 0.75f);

    final float margin = localPaint.measureText(" ");
    String text =
        (inlayHint instanceof TextInlayHintCompat)
            ? ((TextInlayHintCompat) inlayHint).getText()
            : "";
    if (text == null) {
      text = "";
    }
    return localPaint.measureText(text) + margin * 2f;
  }

  @Override
  public void onRender(
      InlayHint inlayHint,
      Canvas canvas,
      Paint paint,
      InlayHintRenderParams params,
      EditorColorScheme colorScheme,
      float measuredWidth) {
    final float centerY = (params.getTextTop() + params.getTextBottom()) / 2f;
    localPaint.setTypeface(paint.getTypeface());
    localPaint.setTextSize(paint.getTextSize() * 0.75f);

    final float margin = localPaint.measureText(" ");
    final float myLineHeight = localPaint.descent() - localPaint.ascent();

    localPaint.setColor(colorScheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND));
    canvas.drawRoundRect(
        margin * 0.5f,
        centerY - myLineHeight / 2f,
        measuredWidth - margin * 0.5f,
        centerY + myLineHeight / 2f,
        params.getTextHeight() * 0.15f,
        params.getTextHeight() * 0.15f,
        localPaint);

    localPaint.setColor(colorScheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND));
    final float myBaseline = centerY + (myLineHeight / 2f - localPaint.descent());

    String text =
        (inlayHint instanceof TextInlayHintCompat)
            ? ((TextInlayHintCompat) inlayHint).getText()
            : "";
    if (text == null) {
      text = "";
    }
    canvas.drawText(text, margin, myBaseline, localPaint);
  }
}
