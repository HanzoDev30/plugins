package ir.hanzodev1375.bootstrap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer;
import io.github.rosemoe.sora.widget.CodeEditor;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class BootstrapPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-bootstrap-lsp.sh";
  private static final String SERVER_ASSET = "bootstrap-lsp.js";
  private static final String ICON_ASSET = "bootstrap.png";
  private static final String SETUP_DIR = "/opt/bootstrap-lsp";

  private static final int RESCAN_DELAY_MS = 260;
  private static final int BIND_CHECK_MS = 1500;
  private static final Set<String> HTML_EXTS = Set.of("html", "htm");

  private PluginContext context;
  private PluginLogger log;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final AtomicLong revision = new AtomicLong();
  private final List<SubscriptionReceipt> receipts = new ArrayList<>();
  private final BootstrapClassIndex classes = new BootstrapClassIndex();

  private ExecutorService bg;
  private CodeEditor editor;
  private Bitmap icon;
  private Runnable pendingRescan;
  private boolean bindCheckScheduled;
  private int lastHintCount = -1;
  private File lastSource;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String install = readAsset(context, INSTALL_SCRIPT_ASSET);
    String server = readAsset(context, SERVER_ASSET);
    if (install.isBlank() || server.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p " + SETUP_DIR
            + "\ncat > " + SETUP_DIR + "/server.js <<'BOOTSTRAP_JS_EOF'\n"
            + server
            + "\nBOOTSTRAP_JS_EOF\ncat > " + SETUP_DIR + "/install.sh <<'INSTALL_EOF'\n"
            + install
            + "\nINSTALL_EOF\nchmod +x " + SETUP_DIR + "/install.sh\n"
            + SETUP_DIR + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-bootstrap-language-server",
            "Install Bootstrap + Emmet + HTML language servers",
            command,
            "Installs Node.js, the bootstrap, vscode-langservers-extracted and "
                + "@olrtg/emmet-language-server npm packages inside the proot rootfs and writes the "
                + "proxy server. Adds Bootstrap class completion and hover to class=\"...\" in .html "
                + "files, next to the normal HTML and Emmet features."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    this.log = context.getLogger();

    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    Disposable lsp =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                new BootstrapLspProvider(launcher, log),
                context.getDescriptor().getId(),
                BootstrapLspProvider.PRIORITY);
    context.registerDisposable(lsp);

    loadIcon();

    bg =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "bootstrap-icons-scan");
              t.setDaemon(true);
              return t;
            });

    String owner = context.getDescriptor().getId();
    context.registerDisposable(
        context.getExtensions().register(IdeEvents.FILE_EVENT, this::onFileEvent, owner, 0));

    main.post(() -> attach(true));
    scheduleBindCheck();

    log.info(
        "Bootstrap language support registered (priority " + BootstrapLspProvider.PRIORITY + ")");
  }

  @Override
  public void deactivate() {
    release();

    if (pendingRescan != null) {
      main.removeCallbacks(pendingRescan);
      pendingRescan = null;
    }
    if (bindCheckScheduled) {
      main.removeCallbacks(bindCheckRunnable);
      bindCheckScheduled = false;
    }

    if (icon != null) {
      icon.recycle();
      icon = null;
    }
    context = null;
    log = null;

    if (bg != null) {
      bg.shutdownNow();
      bg = null;
    }
  }

  private void loadIcon() {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (InputStream in = appContext.getAssets().open(ICON_ASSET)) {
      icon = BitmapFactory.decodeStream(in);
    } catch (IOException | RuntimeException e) {
      log.error("Failed to load " + ICON_ASSET, e);
    }
  }

  private final Runnable bindCheckRunnable =
      () -> {
        bindCheckScheduled = false;
        if (context == null) return;
        attach(false);
        scheduleBindCheck();
      };

  private void scheduleBindCheck() {
    if (bindCheckScheduled || context == null) return;
    bindCheckScheduled = true;
    main.postDelayed(bindCheckRunnable, BIND_CHECK_MS);
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() == null) return;
    FileEvent.Type t = event.type();
    if (t == FileEvent.Type.OPENED || t == FileEvent.Type.SAVED || t == FileEvent.Type.RENAMED) {
      main.post(() -> attach(true));
    }
  }

  private void attach(boolean rescanSame) {
    if (context == null) return;

    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return;

    Object raw = host.getEditor();
    if (!(raw instanceof CodeEditor)) {
      release();
      return;
    }
    CodeEditor ce = (CodeEditor) raw;

    if (editor == ce) {
      if (rescanSame) rescan();
      return;
    }

    release();
    editor = ce;
    ce.registerInlayHintRenderer(new BootstrapIconRenderer(icon));
    receipts.add(
        ce.subscribeEvent(
            ContentChangeEvent.class,
            (ContentChangeEvent event, Unsubscribe unsub) -> scheduleRescan()));

    rescan();
  }

  private void release() {
    for (SubscriptionReceipt r : receipts) {
      try {
        r.unsubscribe();
      } catch (Throwable ignored) {
      }
    }
    receipts.clear();
    editor = null;
  }

  private void scheduleRescan() {
    if (pendingRescan != null) main.removeCallbacks(pendingRescan);
    pendingRescan = this::rescan;
    main.postDelayed(pendingRescan, RESCAN_DELAY_MS);
  }

  private void rescan() {
    CodeEditor ce = editor;
    if (ce == null || bg == null) return;

    long rev = revision.incrementAndGet();
    File file = openFile();
    if (file == null || !isHtmlFile(file)) {
      clear(ce, rev);
      return;
    }

    final File baseDir = file.getParentFile();
    final File projectRoot = findProjectRoot();
    final File fallback = installedCss();
    final String text = ce.getText().toString();
    final int textHash = textHash(text);

    bg.execute(
        () -> {
          classes.refresh(baseDir, projectRoot, fallback);
          List<BootstrapIconHint> hints = new ArrayList<>();
          BootstrapClassScanner.scan(text, classes.names(), hints);
          if (revision.get() != rev) return;
          report(hints.size());
          main.post(() -> apply(ce, hints, rev, textHash));
        });
  }

  private void report(int hints) {
    File src = classes.source();
    boolean sameSource = (src == null ? lastSource == null : src.equals(lastSource));
    if (hints == lastHintCount && sameSource) return;
    lastHintCount = hints;
    lastSource = src;
    if (log != null) {
      log.info("bootstrap icons: " + classes.names().size() + " classes from " + src + ", " + hints + " hints");
    }
  }

  private File installedCss() {
    if (context == null) return null;
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    File rootfs = new File(appContext.getFilesDir(), "rootfs");
    File[] distros = rootfs.listFiles();
    if (distros == null) return null;
    for (File dir : distros) {
      File css = new File(dir, "opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.css");
      if (css.isFile()) return css;
      File min = new File(dir, "opt/bootstrap-lsp/node_modules/bootstrap/dist/css/bootstrap.min.css");
      if (min.isFile()) return min;
    }
    return null;
  }

  private File openFile() {
    if (context == null) return null;
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return null;
    return host.getOpenFile();
  }

  private File findProjectRoot() {
    if (context == null) return null;
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return null;
    return host.getProjectRoot();
  }

  private static boolean isHtmlFile(File file) {
    String name = file.getName().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0) return false;
    return HTML_EXTS.contains(name.substring(dot + 1));
  }

  private static int textHash(CharSequence s) {
    int h = 0;
    for (int i = 0; i < s.length(); i++) {
      h = 31 * h + s.charAt(i);
    }
    return h;
  }

  private void apply(CodeEditor ce, List<BootstrapIconHint> hints, long rev, int textHash) {
    if (revision.get() != rev || editor != ce) return;
    if (currentHash(ce) != textHash) return;
    // Merge instead of replace: drop only hints we own and keep foreign ones (e.g. the host's color
    // renderer) in the same container, so updating text/theme does not knock out the other inlays.
    InlayHintsContainer container = ce.getInlayHints();
    if (container == null) container = new InlayHintsContainer();
    dropOwned(container);
    for (BootstrapIconHint h : hints) container.add(h);
    ce.setInlayHints(container);
    ce.invalidate();
  }

  private void clear(CodeEditor ce, long rev) {
    main.post(
        () -> {
          if (revision.get() != rev || editor != ce) return;
          InlayHintsContainer container = ce.getInlayHints();
          if (container != null) {
            dropOwned(container);
            ce.setInlayHints(container);
            ce.invalidate();
          }
        });
  }

  private static boolean isOwned(InlayHint h) {
    return h != null && BootstrapIconHint.TYPE.equals(h.getType());
  }

  private static void dropOwned(InlayHintsContainer container) {
    int[] lines = container.getLineNumbers();
    for (int line : lines) {
      for (InlayHint h : container.getForLine(line)) {
        if (isOwned(h)) container.remove(h);
      }
    }
  }

  private static int currentHash(CodeEditor ce) {
    return textHash(ce.getText());
  }

  private static String readAsset(PluginContext context, String name) {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}
