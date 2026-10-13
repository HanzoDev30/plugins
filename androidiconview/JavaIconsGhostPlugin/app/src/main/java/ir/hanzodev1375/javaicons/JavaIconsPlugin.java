package ir.hanzodev1375.javaicons;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;
import io.github.rosemoe.sora.widget.CodeEditor;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

public final class JavaIconsPlugin implements GhostPlugin {

  private static final int RESCAN_DELAY_MS = 260;
  private static final int BIND_CHECK_MS = 1500;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final AtomicLong revision = new AtomicLong();
  private final List<SubscriptionReceipt> receipts = new ArrayList<>();

  private PluginContext ctx;
  private PluginLogger log;
  private ExecutorService bg;
  private ResIcons icons;
  private ResValues values;
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
              Thread t = new Thread(r, "resicons-scan");
              t.setDaemon(true);
              return t;
            });

    String owner = ctx.getDescriptor().getId();
    ctx.registerDisposable(
        ctx.getExtensions().register(IdeEvents.FILE_EVENT, this::onFileEvent, owner, 0));

    main.post(() -> attach(true));
    scheduleBindCheck();

    log.info("Res Icons activated");
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

    ctx = null;
    log = null;

    if (bg != null) {
      bg.shutdownNow();
      bg = null;
    }
  }

  // ------- rebind heartbeat -------

  /**
   * The editor activity (and the EDITOR_HOST service pointing to it) is recreated whenever the app
   * triggers a configuration change or {@code Activity.recreate()}. The service must be re-read on
   * every pass: a stale cached host would keep us glued to a destroyed editor and the inlays would
   * stop updating until the plugin is reloaded. This heartbeat re-resolves the host and rebinds to
   * whatever editor is actually current, without rescaling text when nothing changed.
   */
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

  // ------- events -------

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() == null) return;
    FileEvent.Type t = event.type();
    if (t == FileEvent.Type.SAVED && isValuesPath(event.path()) && values != null) {
      values.invalidate();
    }
    if (t == FileEvent.Type.OPENED || t == FileEvent.Type.SAVED || t == FileEvent.Type.RENAMED) {
      main.post(() -> attach(true));
    }
  }

  /** True when a saved file lives under {@code res/values*} or {@code res/color}. */
  private static boolean isValuesPath(String path) {
    if (path == null) return false;
    int i = path.indexOf("/res/");
    if (i < 0) return false;
    String rest = path.substring(i + 5);
    int slash = rest.indexOf('/');
    String dir = slash < 0 ? rest : rest.substring(0, slash);
    return dir.startsWith("values") || "color".equals(dir);
  }

  // ------- attach -------

  private void attach(boolean rescanSame) {
    if (ctx == null) return;

    EditorHost host = ctx.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return;

    Context android = host.getContext();
    if (android == null) {
      android = ctx.getServices().get(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    }
    if (android == null) return;

    if (icons == null) icons = new ResIcons(android);
    if (values == null) values = new ResValues(android);

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
    ce.registerInlayHintRenderer(new ResIconRenderer(icons));
    ce.registerInlayHintRenderer(new TextInlayHintRendererCompat());
    ce.registerInlayHintRenderer(new ColorInlayHintRendererCompat());
    receipts.add(
        ce.subscribeEvent(
            ContentChangeEvent.class,
            (ContentChangeEvent event, Unsubscribe unsub) -> scheduleRescan()));

    rescan();
  }

  /** Drops the current binding and its subscriptions; nothing else. */
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

  // ------- rescan -------

  private void scheduleRescan() {
    if (pendingRescan != null) main.removeCallbacks(pendingRescan);
    pendingRescan = this::rescan;
    main.postDelayed(pendingRescan, RESCAN_DELAY_MS);
  }

  private void rescan() {
    CodeEditor ce = editor;
    if (ce == null || bg == null || icons == null) {
      return;
    }

    long rev = revision.incrementAndGet();
    File file = openFile();
    if (file == null) {
      clear(ce, rev);
      return;
    }
    String name = file.getName().toLowerCase();
    if (!name.endsWith(".java") && !name.endsWith(".xml")) {
      clear(ce, rev);
      return;
    }

    File resDir = findResDir(file);
    if (resDir == null) {
      resDir = findResDir(findProjectRoot());
    }

    final File resolvedRes = resDir;
    final String text = ce.getText().toString();
    final int textHash = textHash(text);
    final ResIcons store = icons;
    final ResValues vals = values;

    if (log != null) log.debug("rescan: res=" + resolvedRes + " textLen=" + text.length());

    bg.execute(
        () -> {
          if (resolvedRes != null) {
            store.setResDir(resolvedRes);
            if (vals != null) vals.setResDir(resolvedRes);
          }
          List<InlayHint> hints = ResIconScanner.scan(text, store, vals);
          if (log != null)
            log.debug("bg-scan: dirs=" + store.dirCount() + " hints=" + hints.size());
          if (revision.get() != rev) return;
          main.post(() -> apply(ce, hints, rev, textHash));
        });
  }

  /** The file behind the current editor, re-read from the live host every time. */
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

  /** از هر مسیری بالا می‌رود تا src/main/res را پیدا کند. */
  private static File findResDir(File start) {
    if (start == null) return null;
    File cur = start.isDirectory() ? start : start.getParentFile();
    int hops = 0;
    while (cur != null && hops++ < 20) {
      if ("java".equals(cur.getName())) {
        File main = cur.getParentFile();
        if (main != null && "main".equals(main.getName())) {
          File res = new File(main, "res");
          if (hasRes(res)) return res;
        }
      }
      if ("main".equals(cur.getName())) {
        File res = new File(cur, "res");
        if (hasRes(res)) return res;
      }
      File res = new File(cur, "res");
      if (hasRes(res)) return res;

      cur = cur.getParentFile();
    }
    return null;
  }

  private static boolean hasRes(File res) {
    if (res == null || !res.isDirectory()) return false;
    File[] files = res.listFiles();
    if (files == null) return false;
    for (File f : files) {
      if (f.isDirectory()) {
        String n = f.getName();
        if (n.startsWith("drawable")
            || n.startsWith("mipmap")
            || n.startsWith("values")
            || "color".equals(n)) {
          return true;
        }
      }
    }
    return false;
  }

  /** String.hashCode over a CharSequence, avoiding an intermediate String copy. */
  private static int textHash(CharSequence s) {
    int h = 0;
    for (int i = 0; i < s.length(); i++) {
      h = 31 * h + s.charAt(i);
    }
    return h;
  }

  private void apply(CodeEditor ce, List<InlayHint> hints, long rev, int textHash) {
    if (revision.get() != rev || editor != ce) return;
    CharSequence current = ce.getText();
    // The scan was computed from a snapshot. If the document moved since (the debounced rescan is
    // still pending), skip: applying hints at now-stale positions is what made the inlays look
    // "stuck" while typing. The pending rescan applies the fresh ones.
    if (textHash(current) != textHash) return;
    if (log != null) log.debug("apply: " + hints.size() + " hints");
    // Merge instead of replace: drop only hints we own and keep foreign ones (e.g. the host's color
    // renderer) in the same container, so updating text/theme does not knock out the other inlays.
    InlayHintsContainer container = ce.getInlayHints();
    if (container == null) container = new InlayHintsContainer();
    dropOwned(container);
    for (InlayHint h : hints) container.add(h);
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
    String t = h == null ? null : h.getType();
    return t != null
        && (t.equals(ResIconHint.TYPE)
            || t.equals(TextInlayHint.TYPE_NAME)
            || t.equals(ColorInlayHintCompat.TYPE_NAME));
  }

  private static void dropOwned(InlayHintsContainer container) {
    int[] lines = container.getLineNumbers();
    for (int line : lines) {
      for (InlayHint h : container.getForLine(line)) {
        if (isOwned(h)) container.remove(h);
      }
    }
  }
}