package ir.hanzodev1375.autorenametag;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;

final class AutoRenameTagEngine {

  private static final long RECONNECT_INTERVAL_MS = 400L;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Runnable reconnect = this::poll;
  private final AutoRenameTagSettings settings;
  private final PluginLogger logger;

  @Nullable private PluginContext context;
  @Nullable private IdeEditor editor;
  @Nullable private SubscriptionReceipt<ContentChangeEvent> receipt;

  private boolean applying;
  private boolean stopped = true;

  AutoRenameTagEngine(AutoRenameTagSettings settings, PluginLogger logger) {
    this.settings = settings;
    this.logger = logger;
  }

  void start(PluginContext context) {
    this.context = context;
    stopped = false;
    main.post(reconnect);
  }

  void stop() {
    stopped = true;
    main.removeCallbacks(reconnect);
    detach();
    context = null;
  }

  private void poll() {
    if (stopped) {
      return;
    }
    IdeEditor current = resolveEditor();
    if (current != editor) {
      detach();
      if (current != null) {
        attach(current);
      }
    }
    main.postDelayed(reconnect, RECONNECT_INTERVAL_MS);
  }

  @Nullable
  private IdeEditor resolveEditor() {
    PluginContext pluginContext = this.context;
    if (pluginContext == null) {
      return null;
    }
    try {
      EditorHost host = pluginContext.getServices().get(IdeHostServices.EDITOR_HOST);
      Object raw = host.getEditor();
      if (raw instanceof IdeEditor ide) {
        return ide;
      }
    } catch (Exception failure) {
      logger.warn("editor host unavailable", failure);
    }
    return null;
  }

  private void attach(IdeEditor target) {
    editor = target;
    receipt = target.subscribeAlways(ContentChangeEvent.class, this::onContentChange);
  }

  private void detach() {
    if (receipt != null) {
      receipt.unsubscribe();
      receipt = null;
    }
    editor = null;
    applying = false;
  }

  private void onContentChange(ContentChangeEvent event) {
    if (applying || stopped || !settings.enabled()) {
      return;
    }
    if (event.isCausedByUndoManager()) {
      return;
    }
    int action = event.getAction();
    if (action != ContentChangeEvent.ACTION_INSERT && action != ContentChangeEvent.ACTION_DELETE) {
      return;
    }
    IdeEditor target = editor;
    if (target == null || !supports(target)) {
      return;
    }
    rename(target, event);
  }

  private boolean supports(IdeEditor target) {
    AutoRenameTagSettings.Scope scope = settings.scope();
    String path = target.getCurrentFilePath();
    String extension = extensionOf(path);
    if (extension.isEmpty()) {
      Object language = target.getEditorLanguage();
      return language != null
          && AutoRenameTagSettings.looksLikeMarkup(language.getClass().getSimpleName());
    }
    return scope.accepts(extension);
  }

  private void rename(IdeEditor target, ContentChangeEvent event) {
    Content content = target.getText();
    CharPosition changeStart = event.getChangeStart();
    CharPosition changeEnd = event.getChangeEnd();
    if (content == null || changeStart == null || changeEnd == null) {
      return;
    }
    boolean insert = event.getAction() == ContentChangeEvent.ACTION_INSERT;
    CharSequence changed = event.getChangedText();
    String text = content.toString();
    TagDocument.Rename plan =
        TagDocument.plan(
            text,
            insert,
            changeStart.index,
            changeEnd.index,
            changed == null ? null : changed.toString());
    if (plan == null) {
      return;
    }
    int caret = insert ? changeEnd.index : changeStart.index;
    int delta = plan.name.length() - (plan.end - plan.start);
    int expected = plan.start < caret ? caret + delta : caret;
    applying = true;
    try {
      content.replace(plan.start, plan.end, plan.name);
      restoreCursor(target, content, expected);
    } catch (Exception failure) {
      logger.warn("linked rename failed", failure);
    } finally {
      applying = false;
    }
  }

  private static void restoreCursor(IdeEditor target, Content content, int expected) {
    if (expected < 0 || expected > content.length()) {
      return;
    }
    Cursor cursor = target.getCursor();
    if (cursor != null && cursor.getLeft() == expected) {
      return;
    }
    CharPosition position = content.getIndexer().getCharPosition(expected);
    target.setSelection(position.line, position.column);
  }

  private static String extensionOf(String path) {
    if (path == null) {
      return "";
    }
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    String name = slash < 0 ? path : path.substring(slash + 1);
    int dot = name.lastIndexOf('.');
    if (dot <= 0 || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }
}
