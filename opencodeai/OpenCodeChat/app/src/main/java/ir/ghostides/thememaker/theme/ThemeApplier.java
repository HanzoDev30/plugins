package ir.ghostides.thememaker.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Saves a generated theme and applies it inside the running GhostIDE host.
 *
 * <p>The host's {@code ThemeManager} reads the active theme in two places, both reachable from any
 * plugin because plugins run inside the host process:
 *
 * <ul>
 *   <li>{@code PreferenceManager.getDefaultSharedPreferences()} → key {@code pref_app_theme_file}:
 *       a path to a theme JSON file (the IDE re-reads it, merged against its defaults);
 *   <li>{@code SharedPreferences("ghost_prefs")} → key {@code theme}: the full merged JSON.
 * </ul>
 *
 * Applying therefore writes the theme file to the app's private directory and sets both keys, so
 * the very next time the IDE reads its theme it picks the image-based theme up.
 */
public final class ThemeApplier {

  public static final String PREF_THEME_FILE = "pref_app_theme_file";
  public static final String GHOST_PREFS = "ghost_prefs";
  public static final String GHOST_THEME_KEY = "theme";

  private ThemeApplier() {}

  public static File themesDir(Context context) {
    File dir = new File(context.getFilesDir(), "themes");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return dir;
  }

  public static File writeThemeFile(Context context, String json, String name) {
    File file = new File(themesDir(context), slug(name) + ".json");
    writeUtf8(file, json);
    return file;
  }

  /** Copies an image (picked through the photo picker) into the theme folder for {@code imagepath}. */
  public static File persistImage(Context context, Uri uri, String name) throws Exception {
    File out = new File(themesDir(context), slug(name) + ".png");
    try (InputStream in = context.getContentResolver().openInputStream(uri);
        OutputStream os = new FileOutputStream(out)) {
      if (in == null) {
        throw new IllegalStateException("Cannot read selected image");
      }
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        os.write(buffer, 0, read);
      }
    }
    return out;
  }

  public static void applyTheme(Context context, String json, File themeFile) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putString(PREF_THEME_FILE, themeFile.getAbsolutePath())
        .commit();

    SharedPreferences ghostPrefs = context.getSharedPreferences(GHOST_PREFS, Context.MODE_PRIVATE);
    ghostPrefs.edit().putString(GHOST_THEME_KEY, json).commit();
  }

  public static File writeUtf8(File file, String content) {
    try (FileOutputStream os = new FileOutputStream(file)) {
      os.write(content.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException("Failed to write " + file, e);
    }
    return file;
  }

  public static String slug(String name) {
    String n = name == null ? "" : name.trim();
    if (n.isEmpty()) {
      n = "ghost-theme";
    }
    String s = n.replaceAll("[^A-Za-z0-9_\\-]+", "-").replaceAll("^-+|-+$", "");
    if (s.isEmpty()) {
      s = "ghost-theme";
    }
    return s.toLowerCase();
  }
}
