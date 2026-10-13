package ir.hanzodev1375.htmlicons;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

public final class HtmlIconsPlugin implements GhostPlugin {

  private static final int RESCAN_DELAY_MS = 260;
  private static final int BIND_CHECK_MS = 1500;

  private static final Set<String> WEB_EXTS =
      new HashSet<>(
          Arrays.asList(
              "html", "htm", "css", "scss", "less", "sass", "js", "mjs", "cjs", "jsx", "ts",
              "tsx", "vue", "php", "asp", "jsp", "aspx", "ejs", "erb", "md","py","json"));

  private final Handler main = new Handler(Looper.getMainLooper());
  private final AtomicLong revision = new AtomicLong();
  private final List<SubscriptionReceipt> receipts = new ArrayList<>();
  private final WebImageCache cache = new WebImageCache();

  private PluginContext ctx;
  private PluginLogger log;
  private ExecutorService bg;
  private CodeEditor editor;
  private Runnable pendingRescan;
  private boolean bindCheckScheduled;

  @Override
  public void activate(PluginContext ctx) {
    this.ctx = ctx;
    this.log = ctx.getLogger();

    bg =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "htmlicons-scan");
              t.setDaemon(true);
              return t;
            });

    String owner = ctx.getDescriptor().getId();
    ctx.registerDisposable(
        ctx.getExtensions().register(IdeEvents.FILE_EVENT, this::onFileEvent, owner, 0));

    main.post(() -> attach(true));
    scheduleBindCheck();

    log.info("Html Icon View activated");
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

    cache.clear();
    ctx = null;
    log = null;

    if (bg != null) {
      bg.shutdownNow();
      bg = null;
    }
  }

  private final Runnable bindCheckRunnable =
      () -> {
        bindCheckScheduled = false;
        if (ctx == null) return;
        attach(false);
        scheduleBindCheck();
      };

  private void scheduleBindCheck() {
    if (bindCheckScheduled || ctx == null) return;
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
    if (ctx == null) return;

    EditorHost host = ctx.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return;

    Context android = host.getContext();
    if (android == null) {
      android = ctx.getServices().get(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    }
    if (android == null) return;

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
    ce.registerInlayHintRenderer(new HtmlIconRenderer(cache));
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
    if (file == null || !isWebFile(file)) {
      clear(ce, rev);
      return;
    }

    File baseDir = file.getParentFile();
    File projectRoot = findProjectRoot();
    final String text = ce.getText().toString();
    final int textHash = textHash(text);
    final File resolvedBase = baseDir;
    final File resolvedRoot = projectRoot;

    bg.execute(
        () -> {
          List<HtmlIconHint> hints = new ArrayList<>();
          WebImageScanner.scan(text, resolvedBase, resolvedRoot, cache, hints);
          if (revision.get() != rev) return;
          main.post(() -> apply(ce, hints, rev, textHash));
        });
  }

  private File openFile() {
    if (ctx == null) return null;
    EditorHost host = ctx.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return null;
    return host.getOpenFile();
  }

  private File findProjectRoot() {
    if (ctx == null) return null;
    EditorHost host = ctx.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return null;
    return host.getProjectRoot();
  }

  private static boolean isWebFile(File file) {
    String name = file.getName().toLowerCase();
    int dot = name.lastIndexOf('.');
    if (dot < 0) return false;
    return WEB_EXTS.contains(name.substring(dot + 1));
  }

  private static int textHash(CharSequence s) {
    int h = 0;
    for (int i = 0; i < s.length(); i++) {
      h = 31 * h + s.charAt(i);
    }
    return h;
  }

  private void apply(CodeEditor ce, List<HtmlIconHint> hints, long rev, int textHash) {
    if (revision.get() != rev || editor != ce) return;
    if (currentHash(ce) != textHash) return;
    // Merge instead of replace: drop only hints we own and keep foreign ones (e.g. the host's color
    // renderer) in the same container, so updating text/theme does not knock out the other inlays.
    InlayHintsContainer container = ce.getInlayHints();
    if (container == null) container = new InlayHintsContainer();
    dropOwned(container);
    for (HtmlIconHint h : hints) container.add(h);
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
    return h != null && HtmlIconHint.TYPE.equals(h.getType());
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
}