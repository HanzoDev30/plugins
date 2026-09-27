package ir.ghostide.composerlsp.checker;

import android.os.Handler;
import android.os.Looper;

import java.io.File;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Keeps exactly one {@link ComposerDependencyChecker} alive per open editor and points it at the
 * file the editor is currently showing.
 *
 * <p>Every tab owns its own {@code IdeEditor}, so a checker is bound to the widget rather than to
 * the path: opening a composer.json in a new tab attaches a checker to that tab's editor, while
 * opening any other file (or leaving the editor) only repaints with a null path, which clears the
 * highlights. File events arrive on background threads for save/delete, so all editor work is
 * posted to the main thread first.
 */
public final class ComposerCheckerService implements Disposable {

  private final PluginContext context;
  private final Handler main = new Handler(Looper.getMainLooper());

  private CodeEditor attachedEditor;
  private ComposerDependencyChecker checker;

  public ComposerCheckerService(PluginContext context) {
    this.context = context;
  }

  /** Re-evaluates the open tab after any file lifecycle event. */
  public void onFileEvent(FileEvent event) {
    String path = event.path();
    main.post(() -> attach(path));
  }

  /** Drops every cached registry answer and re-checks the open tab, used by the manual command. */
  public void recheck() {
    main.post(
        () -> {
          releaseChecker();
          attach(currentPath());
          UiFeedbackHost feedback = context.getServices().get(IdeHostServices.UI_FEEDBACK);
          if (feedback != null) {
            feedback.toast("Checking Packagist for newer Composer versions…", false);
          }
        });
  }

  @Override
  public void dispose() {
    main.post(this::releaseChecker);
  }

  private void attach(String path) {
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) {
      return;
    }
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
    checker = new ComposerDependencyChecker(editor);
    checker.attach();
    checker.setFilePath(path);
    context.getLogger().info("composer dependency checker attached to " + describe(editor));
  }

  private void releaseChecker() {
    if (checker != null) {
      checker.release();
      checker = null;
    }
    attachedEditor = null;
  }

  /** Path of the file the focused editor shows, used when no event path is available. */
  private String currentPath() {
    EditorHost host = context.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) {
      return null;
    }
    Object raw = host.getEditor();
    if (raw instanceof IdeEditor ide) {
      return ide.getCurrentFilePath();
    }
    File open = host.getOpenFile();
    return open == null ? null : open.getAbsolutePath();
  }

  private static String describe(CodeEditor editor) {
    return editor instanceof IdeEditor ide && ide.getCurrentFilePath() != null
        ? ide.getCurrentFilePath()
        : editor.getClass().getSimpleName();
  }
}
