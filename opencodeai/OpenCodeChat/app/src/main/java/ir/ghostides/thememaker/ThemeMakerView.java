package ir.ghostides.thememaker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.ghostides.thememaker.palette.Colors;
import ir.ghostides.thememaker.palette.Palette;
import ir.ghostides.thememaker.palette.PaletteExtractor;
import ir.ghostides.thememaker.theme.GhostThemeBuilder;
import ir.ghostides.thememaker.theme.ThemeApplier;
import ir.ghostides.thememaker.ui.ImagePicker;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The Theme Maker panel: pick an image, it extracts a palette and builds a full GhostIDE theme
 * (activity / editor / widget), which can be applied right away, saved into the open project as a
 * JSON theme file, or exported.
 */
public final class ThemeMakerView {

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler uiThread = new Handler(Looper.getMainLooper());

  private final View root;

  private EditText nameInput;
  private ImageView preview;
  private TextView status;
  private LinearLayout swatches;

  private Bitmap lastBitmap;
  private JSONObject themeJson;
  private String lastImagePath;

  public ThemeMakerView(Context context) {
    this.context = new ContextThemeWrapper(context, R.style.BaseThemeLight);
    this.root = build();
  }

  public View getRoot() {
    return root;
  }

  private View build() {
    View root =
        LayoutInflater.from(context)
            .cloneInContext(context)
            .inflate(R.layout.panel_theme_maker, null, false);

    nameInput = root.findViewById(R.id.theme_name);
    preview = root.findViewById(R.id.theme_preview);
    status = root.findViewById(R.id.theme_status);
    swatches = root.findViewById(R.id.theme_swatches);

    ((Button) root.findViewById(R.id.theme_pick)).setOnClickListener(v -> pickImage());
    ((Button) root.findViewById(R.id.theme_generate)).setOnClickListener(v -> generate());
    ((Button) root.findViewById(R.id.theme_apply)).setOnClickListener(v -> apply());
    ((Button) root.findViewById(R.id.theme_save_project)).setOnClickListener(v -> saveToProject());
    ((Button) root.findViewById(R.id.theme_export)).setOnClickListener(v -> export());

    nameInput.setText("Ghost Theme " + new SimpleDateFormat("HHmmss", Locale.US).format(new Date()));
    return root;
  }

  // ---- actions ----------------------------------------------------------

  private void pickImage() {
    setStatus("pick an image…");
    ImagePicker.pick(
        context,
        new ImagePicker.PickerCallback() {
          @Override
          public void onPicked(Uri uri) {
            setStatus("analyzing image…");
            executor.execute(() -> processUri(uri));
          }

          @Override
          public void onError(String message) {
            setStatus(message);
          }
        });
  }

  private void processUri(Uri uri) {
    Bitmap bitmap = ImagePicker.decodeBitmap(context, uri, 1200);
    if (bitmap == null) {
      ui(() -> setStatus("could not decode the selected image"));
      return;
    }
    String imagePath = null;
    try {
      imagePath =
          ThemeApplier.persistImage(
              context, uri, "theme_image_" + System.currentTimeMillis())
              .getAbsolutePath();
    } catch (Exception ignored) {
      // the image stays optional; the palette is what matters
    }
    final String path = imagePath;
    final Bitmap bmp = bitmap;
    ui(() -> onImageReady(bmp, path));
  }

  private void onImageReady(Bitmap bitmap, String imagePath) {
    lastBitmap = bitmap;
    lastImagePath = imagePath;
    preview.setImageBitmap(bitmap);
    generate();
  }

  private void generate() {
    if (lastBitmap == null) {
      setStatus("pick an image first");
      return;
    }
    executor.execute(
        () -> {
          try {
            Palette palette = PaletteExtractor.extract(lastBitmap);
            JSONObject json =
                GhostThemeBuilder.build(palette, currentName(), lastImagePath);
            ui(() -> onThemeGenerated(json, palette));
          } catch (Exception e) {
            ui(() -> setStatus("theme generation failed: " + e.getMessage()));
          }
        });
  }

  private void onThemeGenerated(JSONObject json, Palette palette) {
    themeJson = json;
    renderSwatches(palette);
    setStatus("theme ready — " + palette.allColors().length + " colors extracted");
  }

  private void apply() {
    if (themeJson == null) {
      setStatus("nothing to apply — pick an image first");
      return;
    }
    File file = ThemeApplier.writeThemeFile(context, themeJson.toString(), currentName());
    ThemeApplier.applyTheme(context, themeJson.toString(), file);
    setStatus("applied ✓ reopen a screen (or restart GhostIDE) to see it");
  }

  private void saveToProject() {
    if (themeJson == null) {
      setStatus("nothing to save — pick an image first");
      return;
    }
    PluginContext plugin = ThemeMakerPlugin.getPluginContext();
    File root = null;
    if (plugin != null) {
      try {
        EditorHost host = plugin.getServices().require(IdeHostServices.EDITOR_HOST);
        root = host.getProjectRoot();
      } catch (Exception ignored) {
      }
    }
    if (root == null) {
      setStatus("editor host unavailable — no project root to save into");
      return;
    }
    File dir = new File(root, "themes");
    if (!dir.exists() && !dir.mkdirs()) {
      setStatus("could not create " + dir.getAbsolutePath());
      return;
    }
    File file = new File(dir, ThemeApplier.slug(currentName()) + ".json");
    ThemeApplier.writeUtf8(file, themeJson.toString());
    setStatus("saved to " + file.getAbsolutePath());
  }

  private void export() {
    if (themeJson == null) {
      setStatus("nothing to export — pick an image first");
      return;
    }
    try {
      File dir = new File(context.getExternalFilesDir(null), "themes");
      if (!dir.exists() && !dir.mkdirs()) {
        setStatus("could not create " + dir.getAbsolutePath());
        return;
      }
      File file = new File(dir, ThemeApplier.slug(currentName()) + ".json");
      ThemeApplier.writeUtf8(file, themeJson.toString());
      setStatus("exported to " + file.getAbsolutePath());
    } catch (Exception e) {
      setStatus("export failed: " + e.getMessage());
    }
  }

  // ---- helpers ----------------------------------------------------------

  private String currentName() {
    String n = nameInput.getText().toString().trim();
    return n.isEmpty() ? "Ghost Theme" : n;
  }

  private void renderSwatches(Palette palette) {
    swatches.removeAllViews();
    int size = dp(26);
    int gap = dp(6);
    int radius = dp(6);
    for (int color : palette.allColors()) {
      View swatch = new View(context);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
      lp.setMargins(0, 0, gap, 0);
      swatch.setLayoutParams(lp);
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(color);
      bg.setCornerRadius(radius);
      bg.setStroke(dp(1), Colors.mix(color, palette.text, 0.25f));
      swatch.setBackground(bg);
      swatches.addView(swatch);
    }
    if (swatches.getChildCount() == 0) {
      setStatus("no colors extracted");
    }
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private void setStatus(String text) {
    status.setText(text);
  }

  private void ui(Runnable r) {
    uiThread.post(r);
  }
}
