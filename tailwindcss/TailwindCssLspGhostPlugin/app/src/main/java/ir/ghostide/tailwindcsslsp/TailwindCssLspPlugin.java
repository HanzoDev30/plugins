package ir.ghostide.tailwindcsslsp;

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

public final class TailwindCssLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-tailwindcss-lsp.sh";
  private static final String SERVER_ASSET = "tailwind-lsp.js";
  private static final String ICON_ASSET = "tailwind.png";
  private static final String INSTALL_DIR = "/opt/tailwindcss-lsp";

  private static final int RESCAN_DELAY_MS = 260;
  private static final int BIND_CHECK_MS = 1500;

  private static final Set<String> EXTENSIONS =
      Set.of(
          "html", "htm", "css", "jsx", "tsx", "js", "ts", "vue", "svelte", "astro", "php", "erb",
          "haml", "slim");

  private PluginContext context;
  private PluginLogger log;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final AtomicLong revision = new AtomicLong();
  private final List<SubscriptionReceipt> receipts = new ArrayList<>();
  private final TailwindClassIndex classes = new TailwindClassIndex();

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
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    String server = readAsset(context, SERVER_ASSET);
    if (body.isBlank() || server.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p "
            + INSTALL_DIR
            + "\ncat > "
            + INSTALL_DIR
            + "/server.js <<'TAILWIND_JS_EOF'\n"
            + server
            + "\nTAILWIND_JS_EOF\ncat > "
            + INSTALL_DIR
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\nchmod +x "
            + INSTALL_DIR
            + "/install.sh\n"
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-tailwindcss-language-server",
            "Install Tailwind CSS language server (@tailwindcss/language-server)",
            command,
            "Installs @tailwindcss/language-server inside the proot rootfs and runs it through "
                + "the plugin's proxy (server.js), which answers the workspace/configuration "
                + "handshake and merges the HTML and Emmet servers. Gives completion and hover "
                + "(the generated CSS of each utility) plus diagnostics and formatting for Tailwind "
                + "CSS classes in html, css, jsx, tsx, vue, svelte, astro, php and other template "
                + "files. Also shows a Tailwind logo inline next to every detected utility class."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    this.log = context.getLogger();

    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    TailwindCssLspProvider provider = new TailwindCssLspProvider(launcher, log);
    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                TailwindCssLspProvider.PRIORITY);
    context.registerDisposable(registration);
    log.info(
        provider.isInstalled()
            ? "tailwindcss-language-server wrapper found in rootfs"
            : "tailwindcss-language-server not installed yet; run the setup action from the Plugin Manager");

    loadIcon();

    bg =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tailwind-icons-scan");
              t.setDaemon(true);
              return t;
            });

    String owner = context.getDescriptor().getId();
    context.registerDisposable(
        context.getExtensions().register(IdeEvents.FILE_EVENT, this::onFileEvent, owner, 0));

    main.post(() -> attach(true));
    scheduleBindCheck();
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
    ce.registerInlayHintRenderer(new TailwindIconRenderer(icon));
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
    if (file == null || !isSupported(file)) {
      clear(ce, rev);
      return;
    }

    final File baseDir = file.getParentFile();
    final File projectRoot = findProjectRoot();
    final File fallback = installedTheme();
    final String text = ce.getText().toString();
    final int textHash = textHash(text);

    bg.execute(
        () -> {
          classes.refresh(baseDir, projectRoot, fallback);
          List<TailwindIconHint> hints = new ArrayList<>();
          TailwindClassScanner.scan(text, classes.names(), hints);
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
      log.info(
          "tailwind icons: "
              + classes.names().size()
              + " classes from "
              + src
              + ", "
              + hints
              + " hints");
    }
  }

  private File installedTheme() {
    if (context == null) return null;
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    File rootfs = new File(appContext.getFilesDir(), "rootfs");
    File[] distros = rootfs.listFiles();
    if (distros == null) return null;
    for (File dir : distros) {
      File bundle =
          new File(
              dir,
              "usr/lib/node_modules/@tailwindcss/language-server/bin/tailwindcss-language-server");
      if (bundle.isFile()) return bundle;
      File local =
          new File(
              dir,
              "usr/local/lib/node_modules/@tailwindcss/language-server/bin/tailwindcss-language-server");
      if (local.isFile()) return local;
      File theme = new File(dir, "opt/tailwindcss-lsp/node_modules/tailwindcss/theme.css");
      if (theme.isFile()) return theme;
      File global = new File(dir, "usr/lib/node_modules/tailwindcss/theme.css");
      if (global.isFile()) return global;
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

  private static boolean isSupported(File file) {
    String name = file.getName().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0) return false;
    return EXTENSIONS.contains(name.substring(dot + 1));
  }

  private static int textHash(CharSequence s) {
    int h = 0;
    for (int i = 0; i < s.length(); i++) {
      h = 31 * h + s.charAt(i);
    }
    return h;
  }

  private void apply(CodeEditor ce, List<TailwindIconHint> hints, long rev, int textHash) {
    if (revision.get() != rev || editor != ce) return;
    if (currentHash(ce) != textHash) return;
    // Merge instead of replace: drop only hints we own and keep foreign ones (e.g. the host's color
    // renderer or the LSP's own hints) in the same container, so updating text/theme does not knock
    // out the other inlays.
    InlayHintsContainer container = ce.getInlayHints();
    if (container == null) container = new InlayHintsContainer();
    dropOwned(container);
    for (TailwindIconHint h : hints) container.add(h);
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
    return h != null && TailwindIconHint.TYPE.equals(h.getType());
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
