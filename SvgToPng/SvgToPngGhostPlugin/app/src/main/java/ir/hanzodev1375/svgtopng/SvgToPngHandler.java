package ir.hanzodev1375.svgtopng;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.ghostide.ide.ui.api.FileClickContext;
import ir.hanzodev1375.ghostide.ide.ui.api.FileClickHandler;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.theme.M3Theme;

/** Claims taps on {@code .svg} files and shows the export dialog. */
public final class SvgToPngHandler implements FileClickHandler {

  private static final int PREVIEW_MAX_DIM = 900;
  private static final int OUTPUT_MAX_DIM = 8192;

  private final PluginContext plugin;
  private final Context pluginContext;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public SvgToPngHandler(PluginContext plugin, Context pluginContext) {
    this.plugin = plugin;
    this.pluginContext = pluginContext;
  }

  @Override
  public boolean supports(String path, String name, boolean isDirectory) {
    return !isDirectory && name != null && name.toLowerCase(Locale.ROOT).endsWith(".svg");
  }

  @Override
  public void onFileClick(FileClickContext click) {
    File file = click.file();
    if (!file.isFile()) {
      toast("File not found: " + click.name());
      return;
    }
    show(click, file);
  }

  private void show(FileClickContext click, File file) {
    Context inflation = new ContextThemeWrapper(pluginContext, 0);
    View content =
        LayoutInflater.from(inflation).inflate(R.layout.dialog_svg_to_png, null, false);

    ImageView preview = content.findViewById(R.id.svg_preview);
    EditText widthInput = content.findViewById(R.id.svg_width);
    EditText heightInput = content.findViewById(R.id.svg_height);
    CheckBox lockRatio = content.findViewById(R.id.svg_lock_ratio);
    TextView fileName = content.findViewById(R.id.svg_file_name);
    TextView status = content.findViewById(R.id.svg_status);

    fileName.setText(file.getName());
    M3Theme.apply(content);
    M3Theme.editText(widthInput);
    M3Theme.editText(heightInput);
    M3Theme.checkboxView(lockRatio);
    applyPreviewBackground(preview);
    styleField(widthInput);
    styleField(heightInput);

    SizeHolder holder = new SizeHolder();
    bindRatio(holder, widthInput, heightInput, lockRatio);

    widthInput.setEnabled(false);
    heightInput.setEnabled(false);
    status.setText("Loading preview...");

    final AlertDialog[] instance = {null};
    DialogCompat builder =
        new DialogCompat(click.context())
            .setTitle(file.getName())
            .setView(content)
            .setCancelable(true)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Export PNG",
                (d, which) ->
                    export(click, instance[0], file, widthInput, heightInput, status));
    instance[0] = builder.create();
    AlertDialog dialog = instance[0];

    io.execute(
        () -> {
          try {
            SVG svg = SvgRenderer.load(file);
            int[] size = SvgRenderer.intrinsic(svg);
            float scale = Math.min(1f, (float) PREVIEW_MAX_DIM / Math.max(size[0], size[1]));
            int previewWidth = Math.max(1, Math.round(size[0] * scale));
            int previewHeight = Math.max(1, Math.round(size[1] * scale));
            Bitmap bitmap = SvgRenderer.raster(svg, previewWidth, previewHeight);

            main.post(
                () -> {
                  if (!dialog.isShowing()) {
                    return;
                  }
                  holder.width = size[0];
                  holder.height = size[1];
                  holder.ratio = (float) size[1] / (float) size[0];
                  holder.ready = true;
                  preview.setImageBitmap(bitmap);
                  widthInput.setText(String.valueOf(size[0]));
                  heightInput.setText(String.valueOf(size[1]));
                  widthInput.setEnabled(true);
                  heightInput.setEnabled(true);
                  status.setText("");
                });
          } catch (Exception error) {
            main.post(
                () -> {
                  if (dialog.isShowing()) {
                    status.setText("Cannot read SVG: " + message(error));
                  }
                });
          }
        });

    dialog.show();
  }

  private void export(
      FileClickContext click,
      AlertDialog dialog,
      File file,
      EditText widthInput,
      EditText heightInput,
      TextView status) {
    Integer width = parse(widthInput);
    Integer height = parse(heightInput);
    if (width == null || width <= 0 || height == null || height <= 0) {
      status.setText("Enter a valid width and height.");
      return;
    }
    if (width > OUTPUT_MAX_DIM || height > OUTPUT_MAX_DIM) {
      status.setText("Maximum size is " + OUTPUT_MAX_DIM + " px.");
      return;
    }

    final int outWidth = width;
    final int outHeight = height;

    View positive = dialog != null ? dialog.getButton(DialogInterface.BUTTON_POSITIVE) : null;
    if (positive != null) {
      positive.setEnabled(false);
    }
    status.setText("Rendering " + outWidth + " x " + outHeight + "...");

    io.execute(
        () -> {
          try {
            SVG svg = SvgRenderer.load(file);
            Bitmap bitmap = SvgRenderer.raster(svg, outWidth, outHeight);

            File output = outputFor(file);
            try (OutputStream stream = new FileOutputStream(output)) {
              if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("PNG encoding failed");
              }
            }
            bitmap.recycle();

            main.post(
                () -> {
                  toast("Saved " + output.getName());
                  if (dialog != null) {
                    dialog.dismiss();
                  }
                });
          } catch (Exception error) {
            main.post(
                () -> {
                  if (dialog != null) {
                    View button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                      button.setEnabled(true);
                    }
                  }
                  status.setText("Export failed: " + message(error));
                });
          }
        });
  }

  private void bindRatio(
      SizeHolder holder, EditText widthInput, EditText heightInput, CheckBox lockRatio) {
    boolean[] syncing = {false};

    widthInput.addTextChangedListener(
        new SimpleWatcher(
            () -> {
              if (syncing[0] || !holder.ready || !lockRatio.isChecked()) {
                return;
              }
              Integer width = parse(widthInput);
              if (width == null || width <= 0) {
                return;
              }
              syncing[0] = true;
              try {
                heightInput.setText(String.valueOf(Math.max(1, Math.round(width * holder.ratio))));
              } finally {
                syncing[0] = false;
              }
            }));

    heightInput.addTextChangedListener(
        new SimpleWatcher(
            () -> {
              if (syncing[0] || !holder.ready || !lockRatio.isChecked()) {
                return;
              }
              Integer height = parse(heightInput);
              if (height == null || height <= 0 || holder.ratio <= 0) {
                return;
              }
              syncing[0] = true;
              try {
                widthInput.setText(String.valueOf(Math.max(1, Math.round(height / holder.ratio))));
              } finally {
                syncing[0] = false;
              }
            }));
  }

  private void applyPreviewBackground(ImageView preview) {
    int fill = color(M3Theme.surfaceContainer(), 0xFF2A2A2A);
    int stroke = color(M3Theme.outlineVariant(), 0x33FFFFFF);

    GradientDrawable previewBackground = new GradientDrawable();
    previewBackground.setColor(fill);
    previewBackground.setCornerRadius(dp(16));
    previewBackground.setStroke(dp(1), stroke);
    preview.setBackground(previewBackground);
  }

  private void styleField(EditText field) {
    int fill = color(M3Theme.surfaceContainerHigh(), 0xFF2A2A2A);
    int stroke = color(M3Theme.outlineVariant(), 0x33FFFFFF);

    GradientDrawable fieldBackground = new GradientDrawable();
    fieldBackground.setColor(fill);
    fieldBackground.setCornerRadius(dp(12));
    fieldBackground.setStroke(dp(1), stroke);
    field.setBackground(fieldBackground);
  }

  private void toast(String message) {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message);
    }
  }

  private static File outputFor(File source) {
    String name = source.getName();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return new File(source.getParentFile(), base + ".png");
  }

  private static Integer parse(EditText input) {
    String text = input.getText().toString().trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private static int color(Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  private static String message(Throwable error) {
    String text = error.getMessage();
    return text != null && !text.isEmpty() ? text : error.getClass().getSimpleName();
  }

  private int dp(int value) {
    return Math.round(value * pluginContext.getResources().getDisplayMetrics().density);
  }

  private static final class SizeHolder {
    boolean ready;
    float ratio = 1f;
    int width;
    int height;
  }

  private static final class SimpleWatcher implements TextWatcher {
    private final Runnable after;

    SimpleWatcher(Runnable after) {
      this.after = after;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      after.run();
    }
  }
}