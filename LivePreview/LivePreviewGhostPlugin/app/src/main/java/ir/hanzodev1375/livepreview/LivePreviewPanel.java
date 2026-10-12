package ir.hanzodev1375.livepreview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

import ir.theme.M3Theme;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Live HTML preview. The project folder is exposed under a virtual
 * {@code https://ghostpreview.local/} origin and served straight from disk through
 * {@link WebViewClient#shouldInterceptRequest}: no sockets, no cleartext HTTP,
 * so nothing is blocked by Android's network security policy.
 */
public class LivePreviewPanel implements EditorPanel {

  private static final String ORIGIN_HOST = "ghostpreview.local";
  private static final String ORIGIN = "https://" + ORIGIN_HOST;
  private static final long RELOAD_DELAY_MS = 350L;
  private static final int MAX_CONSOLE_LINES = 200;

  private static final Set<String> PREVIEWABLE =
      new HashSet<>(Arrays.asList("html", "htm", "xhtml", "svg"));

  private final PluginContext context;
  private final LivePreviewSettings settings;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ArrayDeque<String> consoleLines = new ArrayDeque<>();

  // Accessed from WebView worker threads (shouldInterceptRequest / save events).
  private volatile File serveRoot;
  private volatile boolean autoReload = true;

  // Main thread only.
  private Context viewContext;
  private LinearLayout root;
  private WebView web;
  private TextView pathLabel;
  private TextView autoButton;
  private TextView consoleText;
  private ScrollView consoleScroll;
  private int fg;

  private final Runnable reloadTask =
      () -> {
        if (web != null && web.getUrl() != null) {
          web.reload();
        }
      };

  LivePreviewPanel(PluginContext context, LivePreviewSettings settings) {
    this.context = context;
    this.settings = settings;
    setState(PluginStateMod.FLOATINGWINDOWS);
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.livepreview.panel";
  }

  @Override
  public String getTitle() {
    return "Live Preview";
  }

  // Intentionally not overriding getLastPath(): this panel must not change what
  // CodeRunnerHost.runCurrentFile() executes.

  @Override
  public View createView() {
    viewContext = resolveContext();
    int bg = M3Theme.surface();
    fg = M3Theme.onSurface();

    root = new LinearLayout(viewContext);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg);

    LinearLayout bar = new LinearLayout(viewContext);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);

    bar.addView(
        button(
            "\u25C0",
            v -> {
              if (web != null && web.canGoBack()) web.goBack();
            }),
        cell());
    bar.addView(
        button(
            "\u25B6",
            v -> {
              if (web != null && web.canGoForward()) web.goForward();
            }),
        cell());
    bar.addView(button("\u21BB", v -> refresh()), cell());
    bar.addView(button("File", v -> loadCurrentFile(true)), cell());

    autoButton = button(autoLabel(), v -> toggleAuto());
    bar.addView(autoButton, cell());
    bar.addView(button("Log", v -> toggleConsole()), cell());

    root.addView(
        bar,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    pathLabel = new TextView(viewContext);
    pathLabel.setTextColor(fg);
    pathLabel.setTextSize(12f);
    pathLabel.setAlpha(0.7f);
    pathLabel.setSingleLine(true);
    pathLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    pathLabel.setPadding(dp(12), 0, dp(12), dp(6));
    pathLabel.setText("Starting preview...");
    root.addView(
        pathLabel,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    web = new WebView(viewContext);
    configure(web);
    root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    consoleScroll = new ScrollView(viewContext);
    consoleText = new TextView(viewContext);
    consoleText.setTypeface(Typeface.MONOSPACE);
    consoleText.setTextSize(11f);
    consoleText.setTextColor(fg);
    consoleText.setTextIsSelectable(true);
    consoleText.setPadding(dp(10), dp(6), dp(10), dp(6));
    consoleScroll.addView(consoleText);
    consoleScroll.setVisibility(View.GONE);
    root.addView(
        consoleScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

    startPreview();
    return root;
  }

  /** Called for every SAVED file event; may arrive on any thread. */
  void onFileSaved(String path) {
    if (path == null || !autoReload) return;

    File base = serveRoot;
    if (base == null || !inside(base, new File(path))) return;

    main.removeCallbacks(reloadTask);
    main.postDelayed(reloadTask, RELOAD_DELAY_MS);
  }

  void destroy() {
    main.post(
        () -> {
          main.removeCallbacks(reloadTask);

          if (web != null) {
            web.stopLoading();
            ViewParent parent = web.getParent();
            if (parent instanceof ViewGroup) {
              ((ViewGroup) parent).removeView(web);
            }
            web.destroy();
            web = null;
          }

          serveRoot = null;
          root = null;
        });
  }

  /** Re-applies the current preferences to the live WebView (called from the settings screen). */
  void applyWebSettings() {
    main.post(
        () -> {
          if (web != null) settings.apply(web.getSettings());
        });
  }

  /** Clears the WebView cache (the "Clear preview cache" settings row). */
  void clearCache() {
    main.post(
        () -> {
          if (web != null) web.clearCache(true);
          toast("Preview cache cleared");
        });
  }

  private void startPreview() {
    pathLabel.setText(ORIGIN);
    loadCurrentFile(false);
  }

  private void refresh() {
    if (web == null) return;

    if (web.getUrl() == null) {
      loadCurrentFile(true);
    } else {
      web.reload();
    }
  }

  private void toggleAuto() {
    autoReload = !autoReload;
    autoButton.setText(autoLabel());
    toast(autoReload ? "Auto reload on save: ON" : "Auto reload on save: OFF");
  }

  private void toggleConsole() {
    boolean show = consoleScroll.getVisibility() != View.VISIBLE;
    consoleScroll.setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private String autoLabel() {
    return autoReload ? "Auto \u2713" : "Auto \u2717";
  }

  private void loadCurrentFile(boolean notify) {
    if (web == null) return;

    EditorHost host = editorHost();
    File open = host == null ? null : host.getOpenFile();
    if (open == null) {
      if (notify) toast("Open an HTML file first");
      else log("[PREVIEW] Open an HTML file to start the preview.");
      return;
    }

    File project = host.getProjectRoot();
    File target = pickTarget(open, project);
    if (target == null) {
      if (notify) toast("Nothing to preview: open an .html file");
      else log("[PREVIEW] Nothing to preview yet: open an .html file.");
      return;
    }

    File base = (project != null && inside(project, target)) ? project : target.getParentFile();

    String url = buildUrl(base, target);
    if (url == null) {
      log("[URL ERROR] Cannot preview this file");
      if (notify) toast("Cannot preview this file");
      return;
    }

    serveRoot = base;
    clearConsole();
    log("[LOAD] " + target.getAbsolutePath());
    log("[URL]  " + url);
    web.loadUrl(url);
  }

  private static File pickTarget(File open, File project) {
    if (PREVIEWABLE.contains(extension(open.getName()))) return open;

    File sibling = new File(open.getParentFile(), "index.html");
    if (sibling.isFile()) return sibling;

    if (project != null) {
      File top = new File(project, "index.html");
      if (top.isFile()) return top;
    }
    return null;
  }

  private String buildUrl(File base, File target) {
    try {
      String rel =
          base.getCanonicalFile()
              .toPath()
              .relativize(target.getCanonicalFile().toPath())
              .toString()
              .replace(File.separatorChar, '/');

      if (rel.startsWith("..")) return null;

      Uri.Builder builder = new Uri.Builder().scheme("https").encodedAuthority(ORIGIN_HOST);

      for (String segment : rel.split("/")) {
        if (!segment.isEmpty()) builder.appendPath(segment);
      }
      return builder.build().toString();
    } catch (IOException | IllegalArgumentException e) {
      log("[URL ERROR] " + e.getMessage());
      return null;
    }
  }

  private void configure(WebView w) {
    WebSettings s = w.getSettings();
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    settings.apply(s);

    w.setWebViewClient(
        new WebViewClient() {
          @Override
          public WebResourceResponse shouldInterceptRequest(
              WebView view, WebResourceRequest request) {
            return intercept(request.getUrl());
          }

          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String scheme = request.getUrl().getScheme();
            boolean allowed =
                "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "about".equalsIgnoreCase(scheme)
                    || "data".equalsIgnoreCase(scheme)
                    || "blob".equalsIgnoreCase(scheme);
            return !allowed;
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            if (pathLabel == null || url == null) return;
            pathLabel.setText(url);
          }

          @Override
          public void onReceivedError(
              WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
              log("[WEB ERROR] " + error.getDescription());
            }
          }

          @Override
          public void onReceivedHttpError(
              WebView view,
              WebResourceRequest request,
              android.webkit.WebResourceResponse response) {
            log("[HTTP " + response.getStatusCode() + "] " + request.getUrl());
          }
        });

    w.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public boolean onConsoleMessage(ConsoleMessage m) {
            log(
                "["
                    + m.messageLevel()
                    + "] "
                    + m.message()
                    + " ("
                    + m.sourceId()
                    + ":"
                    + m.lineNumber()
                    + ")");
            return true;
          }

          @Override
          public boolean onJsAlert(WebView v, String url, String message, JsResult r) {
            log("[alert] " + message);
            r.confirm();
            return true;
          }

          @Override
          public boolean onJsConfirm(WebView v, String url, String message, JsResult r) {
            log("[confirm] " + message);
            r.confirm();
            return true;
          }

          @Override
          public boolean onJsPrompt(
              WebView v, String url, String message, String def, JsPromptResult r) {
            log("[prompt] " + message);
            r.cancel();
            return true;
          }
        });
  }

  /**
   * Answers requests for the virtual origin straight from the project folder.
   * Runs on a WebView worker thread. Every path is resolved relative to serveRoot
   * and never outside it. Returning {@code null} lets WebView handle other hosts.
   */
  private WebResourceResponse intercept(Uri uri) {
    String host = uri.getHost();
    if (host == null || !ORIGIN_HOST.equalsIgnoreCase(host)) return null;

    String path = uri.getPath();
    if (path == null || path.isEmpty()) path = "/";

    File base = serveRoot;
    if (base == null) return errorResponse(404, "Preview root is not set");

    try {
      // Uri.getPath() is already percent-decoded; strip the leading slash so the
      // child stays relative to the project folder instead of becoming absolute.
      String relative = path.startsWith("/") ? path.substring(1) : path;
      File file = relative.isEmpty() ? new File(base, "index.html") : new File(base, relative);

      if (file.isDirectory()) {
        File index = new File(file, "index.html");
        if (index.isFile()) {
          file = index;
        }
      }

      File canonicalBase = base.getCanonicalFile();
      File canonicalFile = file.getCanonicalFile();

      if (!inside(canonicalBase, canonicalFile) || !canonicalFile.isFile()) {
        log("[404] " + path);
        return errorResponse(404, "Not Found: " + path);
      }

      // WebResourceResponse wants the BARE mime type ("text/html"); the charset goes in its
      // own argument. A mime like "text/html; charset=utf-8" is not recognised by the renderer
      // and the page is shown as plain text.
      String mime = mimeFor(canonicalFile.getName());
      log("[GET] " + path + " -> " + canonicalFile.getName() + " (" + mime + ")");

      String charset = isTextual(mime) ? "UTF-8" : null;
      InputStream in = new FileInputStream(canonicalFile);
      return new WebResourceResponse(
          mime,
          charset,
          200,
          "OK",
          Collections.<String, String>emptyMap(),
          in);
    } catch (IOException e) {
      log("[500] " + path + " (" + e.getMessage() + ")");
      return errorResponse(500, "Internal Server Error");
    }
  }

  private static WebResourceResponse errorResponse(int code, String message) {
    byte[] body;
    try {
      body = message.getBytes("UTF-8");
    } catch (IOException e) {
      body = new byte[0];
    }
    return new WebResourceResponse(
        "text/plain",
        "UTF-8",
        code,
        String.valueOf(code),
        Collections.<String, String>emptyMap(),
        new ByteArrayInputStream(body));
  }

  /** Text-like types get an explicit UTF-8 charset argument in the response. */
  private static boolean isTextual(String mime) {
    return mime.startsWith("text/")
        || "application/javascript".equals(mime)
        || "application/json".equals(mime)
        || "application/xml".equals(mime);
  }

  /** Returns the bare mime type, WITHOUT any "; charset=..." suffix. */
  private static String mimeFor(String name) {
    String ext = extension(name);
    switch (ext) {
      case "html":
      case "htm":
      case "xhtml":
        return "text/html";
      case "css":
        return "text/css";
      case "js":
      case "mjs":
      case "cjs":
        return "application/javascript";
      case "json":
      case "map":
      case "webmanifest":
        return "application/json";
      case "svg":
        return "image/svg+xml";
      case "wasm":
        return "application/wasm";
      case "txt":
      case "md":
        return "text/plain";
      case "xml":
        return "application/xml";
      case "woff":
        return "font/woff";
      case "woff2":
        return "font/woff2";
      case "ttf":
        return "font/ttf";
      case "otf":
        return "font/otf";
      case "png":
        return "image/png";
      case "jpg":
      case "jpeg":
        return "image/jpeg";
      case "gif":
        return "image/gif";
      case "webp":
        return "image/webp";
      case "avif":
        return "image/avif";
      case "ico":
        return "image/x-icon";
      case "mp3":
        return "audio/mpeg";
      case "mp4":
        return "video/mp4";
      case "webm":
        return "video/webm";
      case "ogg":
        return "application/ogg";
      default:
        String guess = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return guess != null ? guess : "application/octet-stream";
    }
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private void log(String line) {
    main.post(
        () -> {
          consoleLines.addLast(line);
          while (consoleLines.size() > MAX_CONSOLE_LINES) {
            consoleLines.removeFirst();
          }
          if (consoleText != null) {
            consoleText.setText(TextUtils.join("\n", consoleLines));
            consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
          }
        });
  }

  private void clearConsole() {
    consoleLines.clear();
    if (consoleText != null) consoleText.setText("");
  }

  private EditorHost editorHost() {
    return context.getServices().get(IdeHostServices.EDITOR_HOST);
  }

  private Context resolveContext() {
    EditorHost host = editorHost();
    if (host != null && host.getContext() != null) return host.getContext();
    return context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
  }

  private void toast(String message) {
    UiFeedbackHost ui = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (ui != null) ui.toast(message);
  }

  private TextView button(String label, View.OnClickListener click) {
    TextView t = new TextView(viewContext);
    t.setText(label);
    t.setTextColor(fg);
    t.setTextSize(14f);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(4), dp(12), dp(4), dp(12));
    t.setClickable(true);
    t.setOnClickListener(click);
    return t;
  }

  private static LinearLayout.LayoutParams cell() {
    return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
  }

  private int dp(int v) {
    return Math.round(v * viewContext.getResources().getDisplayMetrics().density);
  }

  private static boolean inside(File root, File f) {
    try {
      String r = root.getCanonicalPath();
      String c = f.getCanonicalPath();
      return c.equals(r) || c.startsWith(r.endsWith(File.separator) ? r : r + File.separator);
    } catch (IOException e) {
      return false;
    }
  } 
}
