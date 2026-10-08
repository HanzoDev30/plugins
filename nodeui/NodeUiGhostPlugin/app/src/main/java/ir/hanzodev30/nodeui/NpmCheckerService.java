package ir.hanzodev30.nodeui;

import android.os.Handler;
import android.os.Looper;

import java.io.File;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class NpmCheckerService implements Disposable {

  private final PluginContext context;
  private final android.content.Context androidContext;
  private final Handler main = new Handler(Looper.getMainLooper());

  private CodeEditor attachedEditor;
  private NpmDependencyChecker checker;

  public NpmCheckerService(PluginContext context) {
    this.context = context;
    this.androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
  }

  public void onFileEvent(FileEvent event) {
    String path = event.path();
    main.post(() -> attach(path));
  }

  @Override
  public void dispose() {
    main.post(this::releaseChecker);
  }

  private void attach(String path) {
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) return;
    Object raw = host.getEditor();
    if (!(raw instanceof CodeEditor editor)) {
      releaseChecker();
      return;
    }
    if (checker != null && attachedEditor == editor) {
      checker.setFilePath(path);
      return;
    }
    releaseChecker();
    attachedEditor = editor;
    checker = new NpmDependencyChecker(editor, androidContext);
    checker.attach();
    checker.setFilePath(path);
    String detail = editor instanceof IdeEditor ide ? describe(ide) : editor.getClass().getSimpleName();
    context.getLogger().info("npm dependency checker attached to " + detail);
  }

  private void releaseChecker() {
    if (checker != null) {
      checker.release();
      checker = null;
    }
    attachedEditor = null;
  }

  private static String describe(IdeEditor editor) {
    String path = editor.getCurrentFilePath();
    return path != null ? path : editor.getClass().getSimpleName();
  }
}