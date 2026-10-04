package ir.hanzodev1375.csv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEventListener;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class CsvPlugin implements GhostPlugin {

  private static final long TICK_MS = 1000L;
  private static final long[] APPLY_DELAYS_MS = {0L, 250L, 700L, 1500L, 3000L};

  private final Handler main = new Handler(Looper.getMainLooper());
  private final java.util.Map<String, String> scopeCache = new java.util.HashMap<>();
  private String lastLoggedPath;
  private PluginContext context;
  private CsvViewerPanel panel;

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);

    CsvTextmateHost.create(androidContext, context.getLogger());
    CsvTextmateHost.instance().loadAsync();

    panel = new CsvViewerPanel(context);
    Disposable panelReg =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                panel,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(panelReg);

    Disposable fileEvents =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                (FileEventListener) this::onFileEvent,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(fileEvents);

    main.postDelayed(ticker, TICK_MS);
    context.getLogger().info("CSV viewer + grammars registered");
  }

  @Override
  public void deactivate() {
    main.removeCallbacks(ticker);
  }

  /** Follows the current tab: works for restored/re-focused tabs where OPENED never fires. */
  private final Runnable ticker =
      new Runnable() {
        @Override
        public void run() {
          try {
            checkCurrentTab();
          } catch (RuntimeException e) {
            context.getLogger().warn("csv: tick failed", e);
          }
          main.postDelayed(this, TICK_MS);
        }
      };

  private void checkCurrentTab() {
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    File open = editorHost.getOpenFile();
    String path = open == null ? null : open.getAbsolutePath();
    if (path == null || !isTabular(path)) {
      lastLoggedPath = null;
      return;
    }
    String scope = scopeCache.get(path);
    if (scope == null) {
      scope = CsvTextmateHost.scopeFor(sniffDelimiter(path));
      scopeCache.put(path, scope);
    }
    if (!path.equals(lastLoggedPath)) {
      lastLoggedPath = path;
      Object raw = editorHost.getEditor();
      context.getLogger().info(
          "csv: current tab " + open.getName() + " scope=" + scope + " editor="
              + (raw == null ? "null" : raw.getClass().getName()));
    }
    applyGrammar(path, scope);
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.path() == null || !isTabular(event.path())) {
      return;
    }
    if (event.type() == FileEvent.Type.OPENED) {
      final String path = event.path();
      final String scope = CsvTextmateHost.scopeFor(sniffDelimiter(path));
      for (long delay : APPLY_DELAYS_MS) {
        main.postDelayed(() -> applyGrammar(path, scope), delay);
      }
    }
    if (event.type() == FileEvent.Type.SAVED) {
      scopeCache.remove(event.path());
    }
    if (event.type() == FileEvent.Type.OPENED || event.type() == FileEvent.Type.SAVED) {
      main.post(() -> panel.refresh());
    }
  }

  private void applyGrammar(String path, String scope) {
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    File open = editorHost.getOpenFile();
    if (open != null && !open.getAbsolutePath().equals(new File(path).getAbsolutePath())) {
      return; // another tab is current; a later attempt will catch ours
    }
    Object raw = editorHost.getEditor();
    if (!(raw instanceof CodeEditor editor)) {
      return;
    }
    CsvTextmateHost host = CsvTextmateHost.instance();
    if (host != null) {
      host.setWhenReady(editor, scope);
    }
  }

  private static boolean isTabular(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    return p.endsWith(".csv") || p.endsWith(".tsv") || p.endsWith(".psv") || p.endsWith(".tab");
  }

  private static char sniffDelimiter(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    if (p.endsWith(".tsv") || p.endsWith(".tab")) {
      return '\t';
    }
    if (p.endsWith(".psv")) {
      return '|';
    }
    try (InputStream in = new FileInputStream(new File(path))) {
      byte[] buf = new byte[8192];
      int n = in.read(buf);
      if (n > 0) {
        return CsvParser.detectDelimiter(new String(buf, 0, n, StandardCharsets.UTF_8));
      }
    } catch (IOException | RuntimeException ignored) {
      // fall through to comma
    }
    return ',';
  }
}
