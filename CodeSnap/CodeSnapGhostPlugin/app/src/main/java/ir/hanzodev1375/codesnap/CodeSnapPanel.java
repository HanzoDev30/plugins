package ir.hanzodev1375.codesnap;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.theme.M3Theme;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;

public class CodeSnapPanel implements EditorPanel {

  private final PluginContext pluginContextApi;
  private final Context pluginContext;
  private final Handler main = new Handler(Looper.getMainLooper());
  private ViewGroup root;
  private WebView web;
  private TextView status;
  private Spinner langSpinner;
  private Spinner themeSpinner;
  private String[] langNames;
  private String[] themeNames;

  private int langIndex;
  private int themeIndex;
  private Uri lastImage;
  private String lastCode;
  private String lastLang;
  private volatile boolean pageReady;

  private final Runnable autoTick =
      new Runnable() {
        @Override
        public void run() {
          if (web == null || root == null) return;
          if (!pageReady) {
            main.postDelayed(this, 300);
            return;
          }
          EditorHost host = editorHost();
          String code = selectedCode(host);
          String lang = resolveLang(host);
          if (code.isEmpty()) {
            if (lastCode != null) {
              lastCode = null;
              status.setText("Select code in the editor, then tap Snap");
            }
          } else if (!code.equals(lastCode) || !lang.equals(lastLang)) {
            lastCode = code;
            lastLang = lang;
            status.setText(code.length() + " chars selected - preview ready");
            render(code, lang, false);
          }
          main.postDelayed(this, 500);
        }
      };

  private void firstRenderTick() {
    main.removeCallbacks(autoTick);
    main.post(autoTick);
  }

  CodeSnapPanel(Context context, PluginContext pluginContextApi) {
    this.pluginContext =
        new ContextThemeWrapper(context, android.R.style.ThemeOverlay_Material_Dark_ActionBar);
    this.pluginContextApi = pluginContextApi;
    setState(PluginStateMod.BOTTOMSHEETDIALOG);
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.codesnap.panel";
  }

  @Override
  public String getTitle() {
    return "Code Snap";
  }

  @Override
  public View createView() {
    int fg = c(M3Theme.onSurface(), 0xFFE6E1E5);

    // inflate(..., null, false) drops the root's layout_width/height, so the host used to
    // add the view with its own default params. PanelRoot gives it explicit MATCH_PARENT
    // params and a fixed fallback height when the host does not hand us an exact one.
    View content =
        LayoutInflater.from(pluginContext)
            .cloneInContext(pluginContext)
            .inflate(R.layout.codesnap_panel, null, false);
    PanelRoot frame =
        new PanelRoot(
            pluginContext,
            Math.round(pluginContext.getResources().getDisplayMetrics().heightPixels * 0.75f));
    frame.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    frame.setBackgroundColor(c(M3Theme.surface(), 0xFF1C1B1F));
    frame.addView(
        content,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    root = frame;

    status = root.findViewById(R.id.codesnap_status);
    langSpinner = root.findViewById(R.id.codesnap_lang);
    themeSpinner = root.findViewById(R.id.codesnap_theme);
    ViewGroup webContainer = root.findViewById(R.id.codesnap_web_container);

    status.setTextColor(fg);
    status.setAlpha(0.7f);
    tint(root.findViewById(R.id.codesnap_snap), fg);
    tint(root.findViewById(R.id.codesnap_share), fg);

    root.findViewById(R.id.codesnap_snap).setOnClickListener(v -> snap());
    root.findViewById(R.id.codesnap_share).setOnClickListener(v -> share());

    web = new WebView(pluginContext);
    pageReady = false;
    configure(web);
    webContainer.addView(
        web,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    File index = prepareWeb();
    if (index != null) {
      web.loadUrl(Uri.fromFile(index).toString());
    } else {
      status.setText("Cannot prepare highlight.js assets");
    }
    setupSpinners(fg);
    main.postDelayed(autoTick, 500);
    return root;
  }

  private static int c(Integer v, int fallback) {
    return v != null ? v : fallback;
  }

  /** Fills the panel when the host gives an exact size; otherwise uses a fixed height. */
  private static final class PanelRoot extends FrameLayout {
    private final int fallbackHeight;

    PanelRoot(Context context, int fallbackHeight) {
      super(context);
      this.fallbackHeight = fallbackHeight;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      if (MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST) {
        widthSpec =
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.EXACTLY);
      }
      int mode = MeasureSpec.getMode(heightSpec);
      if (mode != MeasureSpec.EXACTLY) {
        int size = MeasureSpec.getSize(heightSpec);
        int target =
            (mode == MeasureSpec.AT_MOST && size > 0)
                ? Math.min(fallbackHeight, size)
                : fallbackHeight;
        heightSpec = MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY);
      }
      super.onMeasure(widthSpec, heightSpec);
    }
  }

  private void setupSpinners(int fg) {
    langNames = readJson("web/languages.json");
    themeNames = readJson("web/styles.json");
    if (langNames == null) langNames = new String[] {"auto"};
    if (themeNames == null) themeNames = new String[] {"atom-one-dark"};
    String[] langs = new String[langNames.length + 1];
    langs[0] = "auto";
    System.arraycopy(langNames, 0, langs, 1, langNames.length);
    langNames = langs;

    styleSpinner(langSpinner);
    styleSpinner(themeSpinner);
    langSpinner.setAdapter(spinnerAdapter(langNames, fg));
    themeSpinner.setAdapter(spinnerAdapter(themeNames, fg));
    langSpinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View view, int position, long id) {
            langIndex = position;
            refreshPreview();
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    themeSpinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View view, int position, long id) {
            themeIndex = position;
            if (web != null) {
              web.evaluateJavascript(
                  "window.setTheme(" + jsonString(themeNames[position]) + ")", null);
            }
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
  }

  private ArrayAdapter<String> spinnerAdapter(String[] names, int fg) {
    ArrayAdapter<String> adapter =
        new ArrayAdapter<String>(
            pluginContext, android.R.layout.simple_spinner_dropdown_item, names) {
          @Override
          public android.view.View getView(
              int position, android.view.View convertView, android.view.ViewGroup parent) {
            android.view.View v = super.getView(position, convertView, parent);
            if (v instanceof TextView) {
              TextView t = (TextView) v;
              t.setTextColor(fg);
              t.setTextSize(13);
              t.setSingleLine(true);
              t.setEllipsize(TextUtils.TruncateAt.END);
              t.setPaddingRelative(0, 0, 0, 0);
            }
            return v;
          }

          @Override
          public android.view.View getDropDownView(
              int position, android.view.View convertView, android.view.ViewGroup parent) {
            android.view.View v = super.getDropDownView(position, convertView, parent);
            if (v instanceof TextView) ((TextView) v).setTextColor(fg);
            return v;
          }
        };
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    langSpinnerSet(adapter);
    return adapter;
  }

  private void styleSpinner(Spinner s) {
    s.setBackground(spinnerBackground());
    s.setPaddingRelative(dp(12), 0, dp(30), 0);
    s.setPopupBackgroundDrawable(popupBackground());
    s.setDropDownWidth(dp(200));
  }

  private Drawable spinnerBackground() {
    GradientDrawable box = new GradientDrawable();
    box.setColor(c(M3Theme.surfaceContainerHigh(), 0xFF2B2930));
    box.setCornerRadius(dp(12));
    box.setStroke(dp(1), c(M3Theme.outlineVariant(), 0xFF49454F));
    return new LayerDrawable(
        new Drawable[] {box, new ChevronDrawable(c(M3Theme.onSurfaceVariant(), 0xFFCAC4D0))});
  }

  private Drawable popupBackground() {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c(M3Theme.surfaceContainer(), 0xFF211F26));
    d.setCornerRadius(dp(12));
    d.setStroke(dp(1), c(M3Theme.outlineVariant(), 0xFF49454F));
    return d;
  }

  /** Small dropdown arrow drawn at the end edge (RTL aware). */
  private final class ChevronDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    ChevronDrawable(int color) {
      paint.setColor(color);
    }

    @Override
    public void draw(Canvas canvas) {
      Rect b = getBounds();
      boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
      float cx = rtl ? b.left + dp(16) : b.right - dp(16);
      float cy = b.exactCenterY();
      float hw = dp(10) / 2f;
      float hh = dp(5) / 2f;
      path.reset();
      path.moveTo(cx - hw, cy - hh);
      path.lineTo(cx + hw, cy - hh);
      path.lineTo(cx, cy + hh);
      path.close();
      canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  private void langSpinnerSet(ArrayAdapter<String> adapter) {}

  private String[] readJson(String assetPath) {
    try {
      File f =
          new File(
              pluginContext.getCacheDir(),
              "codesnap_web/" + assetPath.substring(assetPath.lastIndexOf('/') + 1));
      java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null) sb.append(line);
      r.close();
      JSONArray arr = new JSONArray(sb.toString());
      String[] out = new String[arr.length()];
      for (int i = 0; i < arr.length(); i++) out[i] = arr.getString(i);
      return out;
    } catch (Throwable t) {
      return null;
    }
  }

  private void refreshPreview() {
    if (web == null || lastCode == null) return;
    EditorHost host = editorHost();
    String code = selectedCode(host);
    if (code.isEmpty()) return;
    lastLang = resolveLang(host);
    render(code, lastLang, false);
  }

  private File prepareWeb() {
    String[] files = {
      "index.html", "highlight.min.js", "languages.min.js", "languages.json", "styles.json"
    };
    try {
      File dir = new File(pluginContext.getCacheDir(), "codesnap_web");
      if (!dir.exists() && !dir.mkdirs()) return null;
      for (String f : files) {
        copyAsset("web/" + f, new File(dir, f));
      }
      String[] styles = pluginContext.getAssets().list("web/styles");
      if (styles != null) {
        File sdir = new File(dir, "styles");
        if (!sdir.exists()) sdir.mkdirs();
        for (String f : styles) {
          copyAsset("web/styles/" + f, new File(sdir, f));
        }
      }
      return new File(dir, "index.html");
    } catch (Throwable t) {
      status.setText("Assets: " + t.getMessage());
      return null;
    }
  }

  private void copyAsset(String assetPath, File out) throws IOException {
    try (InputStream in = pluginContext.getAssets().open(assetPath);
        OutputStream os = new FileOutputStream(out)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        os.write(buf, 0, n);
      }
    }
  }

  void onFileSaved(String path) {}

  void destroy() {
    main.removeCallbacks(autoTick);
    main.post(
        () -> {
          if (web != null) {
            web.stopLoading();
            web.removeJavascriptInterface("CodeSnapBridge");
            ViewParent parent = web.getParent();
            if (parent instanceof ViewGroup) {
              ((ViewGroup) parent).removeView(web);
            }
            web.destroy();
            web = null;
          }
          root = null;
        });
  }

  private void snap() {
    if (web == null || status == null) {
      toast("Open the Code Snap panel first");
      return;
    }
    EditorHost host = editorHost();
    String code = selectedCode(host);
    if (code.isEmpty()) {
      status.setText("Nothing to snap: select code in the editor");
      toast("Select code first");
      return;
    }
    lastCode = code;
    lastLang = resolveLang(host);
    status.setText("Rendering " + code.length() + " chars...");
    render(code, lastLang, true);
  }

  private void render(String code, String lang, boolean thenCapture) {
    if (web == null) return;
    String name = fileName(editorHost());
    web.evaluateJavascript(
        "window.snap("
            + jsonString(code)
            + ","
            + jsonString(lang)
            + ","
            + jsonString(name.isEmpty() ? "CodeSnap" : name)
            + ")",
        value -> {
          // The whole card is rasterised inside the page (SVG foreignObject -> canvas), so the
          // result no longer depends on how tall the WebView currently is on screen.
          if (thenCapture && web != null) web.evaluateJavascript("window.captureImage()", null);
        });
  }

  /** Called from the page (JavaBridge thread) with the finished PNG as base64. */
  public final class Bridge {
    @JavascriptInterface
    public void onImage(String base64) {
      Uri saved = null;
      String error = null;
      try {
        saved = save(Base64.decode(base64, Base64.DEFAULT));
      } catch (Throwable t) {
        error = "Save failed: " + t.getMessage();
      }
      final Uri uri = saved;
      final String err = error;
      main.post(
          () -> {
            if (status == null) return;
            if (uri != null) {
              lastImage = uri;
              status.setText("Saved to Pictures/CodeSnap");
              toast("Code image saved");
            } else {
              status.setText(err != null ? err : "Cannot save image");
            }
          });
    }

    @JavascriptInterface
    public void onError(String message) {
      main.post(
          () -> {
            if (status != null) status.setText("Render failed: " + message);
          });
    }
  }

  private Uri save(byte[] png) throws IOException {
    String name = "codesnap_" + System.currentTimeMillis() + ".png";
    if (Build.VERSION.SDK_INT >= 29) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
      values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
      values.put(
          MediaStore.Images.Media.RELATIVE_PATH,
          Environment.DIRECTORY_PICTURES + File.separator + "CodeSnap");
      values.put(MediaStore.Images.Media.IS_PENDING, 1);
      Uri uri =
          pluginContext
              .getContentResolver()
              .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      if (uri == null) throw new IOException("Cannot create image in gallery");
      try (OutputStream out = pluginContext.getContentResolver().openOutputStream(uri)) {
        if (out == null) throw new IOException("null stream");
        out.write(png);
      } catch (IOException e) {
        pluginContext.getContentResolver().delete(uri, null, null);
        throw e;
      }
      values.clear();
      values.put(MediaStore.Images.Media.IS_PENDING, 0);
      pluginContext.getContentResolver().update(uri, values, null, null);
      return uri;
    }

    File dir = null;
    try {
      File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
      dir = new File(pictures, "CodeSnap");
      if (!dir.exists() && !dir.mkdirs()) dir = null;
    } catch (Throwable ignored) {
      dir = null;
    }
    if (dir == null) {
      File filesDir = pluginContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
      if (filesDir == null) throw new IOException("No writable storage");
      dir = new File(filesDir, "CodeSnap");
      dir.mkdirs();
    }
    File outFile = new File(dir, name);
    try (FileOutputStream fos = new FileOutputStream(outFile)) {
      fos.write(png);
    }
    MediaScannerConnection.scanFile(
        pluginContext, new String[] {outFile.getAbsolutePath()}, new String[] {"image/png"}, null);
    return Uri.fromFile(outFile);
  }

  private void share() {
    if (lastImage == null) {
      toast("Snap an image first");
      return;
    }
    try {
      Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setType("image/png");
      intent.putExtra(Intent.EXTRA_STREAM, lastImage);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      pluginContext.startActivity(Intent.createChooser(intent, "Share code image"));
    } catch (Throwable t) {
      toast("Cannot share: " + lastImage);
    }
  }

  private String resolveLang(EditorHost host) {
    if (langIndex > 0 && langNames != null && langIndex < langNames.length) {
      return langNames[langIndex];
    }
    String ext = extension(fileName(host));
    switch (ext) {
      case "kt":
      case "kts":
        return "kotlin";
      case "py":
        return "python";
      case "js":
      case "jsx":
      case "mjs":
      case "cjs":
        return "javascript";
      case "ts":
      case "tsx":
        return "typescript";
      case "c":
      case "h":
        return "c";
      case "cpp":
      case "cc":
      case "hpp":
        return "cpp";
      case "cs":
        return "csharp";
      case "rb":
        return "ruby";
      case "sh":
      case "bash":
      case "zsh":
        return "bash";
      case "ps1":
        return "powershell";
      case "yml":
      case "yaml":
        return "yaml";
      case "md":
        return "markdown";
      case "htm":
      case "html":
      case "vue":
      case "svelte":
        return "xml";
      case "scss":
      case "less":
        return "scss";
      case "pl":
        return "perl";
      case "m":
        return "objectivec";
      case "hs":
        return "haskell";
      case "ex":
        return "elixir";
      case "erl":
        return "erlang";
      case "gradle":
        return "gradle";
      default:
        return ext;
    }
  }

  private static String selectedCode(EditorHost host) {
    if (host == null) return "";
    Object raw = host.getEditor();
    if (!(raw instanceof CodeEditor)) return "";
    CodeEditor editor = (CodeEditor) raw;
    Cursor cursor = editor.getCursor();
    if (cursor == null || !cursor.isSelected()) return "";
    return editor
        .getText()
        .subContent(
            cursor.getLeftLine(),
            cursor.getLeftColumn(),
            cursor.getRightLine(),
            cursor.getRightColumn())
        .toString();
  }

  private static String fileName(EditorHost host) {
    if (host == null) return "";
    File f = host.getOpenFile();
    return f == null ? "" : f.getName();
  }

  private static String extension(String name) {
    if (name == null) return "";
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String jsonString(String s) {
    StringBuilder b = new StringBuilder(s.length() + 16);
    b.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          b.append("\\\"");
          break;
        case '\\':
          b.append("\\\\");
          break;
        case '\n':
          b.append("\\n");
          break;
        case '\r':
          b.append("\\r");
          break;
        case '\t':
          b.append("\\t");
          break;
        default:
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
      }
    }
    b.append('"');
    return b.toString();
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void configure(WebView w) {
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(false);
    s.setCacheMode(WebSettings.LOAD_NO_CACHE);
    s.setUseWideViewPort(true);
    s.setLoadWithOverviewMode(true);
    s.setTextZoom(100);
    w.addJavascriptInterface(new Bridge(), "CodeSnapBridge");
    w.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView view, String url) {
            pageReady = true;
            // The spinner's first selection fires before the page exists, so apply it here.
            if (themeNames != null && themeIndex >= 0 && themeIndex < themeNames.length) {
              view.evaluateJavascript(
                  "window.setTheme(" + jsonString(themeNames[themeIndex]) + ")", null);
            }
            firstRenderTick();
          }
        });
  }

  private static void tint(TextView t, int color) {
    if (t != null) t.setTextColor(color);
  }

  private EditorHost editorHost() {
    return pluginContextApi.getServices().get(IdeHostServices.EDITOR_HOST);
  }

  private void toast(String message) {
    UiFeedbackHost ui = pluginContextApi.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (ui != null) ui.toast(message);
  }

  private int dp(int v) {
    return Math.round(v * pluginContext.getResources().getDisplayMetrics().density);
  }
}
