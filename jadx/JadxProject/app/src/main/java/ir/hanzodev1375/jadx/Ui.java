package ir.hanzodev1375.jadx;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.theme.M3Theme;

import java.util.Locale;

/** View helpers. All colors come from {@link M3Theme} so the dialogs follow the active theme. */
final class Ui {

  private Ui() {}

  static int dp(Context context, float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private static int or(Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  static int surface() {
    return or(M3Theme.surfaceContainerHigh(), 0xFF2B2930);
  }

  static int onSurface() {
    return or(M3Theme.onSurface(), 0xFFE6E1E5);
  }

  static int onSurfaceVariant() {
    return or(M3Theme.onSurfaceVariant(), 0xFFCAC4D0);
  }

  static int primary() {
    return or(M3Theme.primary(), 0xFFD0BCFF);
  }

  static int onPrimary() {
    return or(M3Theme.onPrimary(), 0xFF381E72);
  }

  static int outline() {
    return or(M3Theme.outlineVariant(), 0x33FFFFFF);
  }

  static GradientDrawable box(Context context, int fill, int stroke, float radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(fill);
    drawable.setCornerRadius(dp(context, radiusDp));
    if (stroke != 0) {
      drawable.setStroke(dp(context, 1), stroke);
    }
    return drawable;
  }

  static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
    TextView view = new TextView(context);
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(color);
    if (bold) {
      view.setTypeface(view.getTypeface(), Typeface.BOLD);
    }
    return view;
  }

  static TextView button(Context context, CharSequence label, View.OnClickListener listener) {
    TextView view = text(context, label, 14, onPrimary(), true);
    view.setGravity(Gravity.CENTER);
    view.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 10));
    GradientDrawable fill = box(context, primary(), 0, 20);
    view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, null));
    view.setClickable(true);
    view.setOnClickListener(listener);
    return view;
  }

  /** A list row with a title and a smaller second line. The two TextViews are kept in the tag. */
  static LinearLayout twoLineRow(Context context) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

    TextView title = text(context, "", 15, onSurface(), false);
    title.setSingleLine(true);
    title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    title.setTextDirection(View.TEXT_DIRECTION_LTR);

    TextView subtitle = text(context, "", 12, onSurfaceVariant(), false);
    subtitle.setSingleLine(true);
    subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    subtitle.setTextDirection(View.TEXT_DIRECTION_LTR);

    row.addView(title);
    row.addView(subtitle);
    row.setTag(new TextView[] {title, subtitle});
    return row;
  }

  static void bindRow(View row, CharSequence title, CharSequence subtitle) {
    TextView[] views = (TextView[]) row.getTag();
    views[0].setText(title);
    views[1].setText(subtitle);
    views[1].setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
  }

  /**
   * Colors every case-insensitive occurrence of {@code query} inside {@code text} with the given
   * M3 color and makes it bold. Returns {@code text} untouched when there is nothing to mark.
   */
  static CharSequence highlightMatches(CharSequence text, String query, int color) {
    if (text == null || query == null || query.isEmpty()) {
      return text;
    }
    String haystack = text.toString();
    String lowerHaystack = haystack.toLowerCase(Locale.ROOT);
    String needle = query.toLowerCase(Locale.ROOT);
    SpannableStringBuilder builder = null;
    int from = 0;
    while (true) {
      int index = lowerHaystack.indexOf(needle, from);
      if (index < 0) {
        break;
      }
      if (builder == null) {
        builder = new SpannableStringBuilder(text);
      }
      int end = index + needle.length();
      builder.setSpan(
          new ForegroundColorSpan(color), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      builder.setSpan(
          new StyleSpan(Typeface.BOLD), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      from = end;
    }
    return builder != null ? builder : text;
  }
}
