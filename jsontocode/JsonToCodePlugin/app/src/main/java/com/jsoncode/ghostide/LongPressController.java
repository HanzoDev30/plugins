package com.jsoncode.ghostide;

import android.os.Handler;
import android.os.Looper;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Watches the active editor for long presses and turns them into a JSON selection.
 *
 * <p>Two things are required for this to work. The subscription must follow the editor instance,
 * because the host swaps in a new {@code IdeEditor} for every opened file and exposes no change
 * listener, so the current editor is re-resolved on a timer. And the plugin must resolve the
 * selection itself: Sora dispatches {@link LongPressEvent} <em>before</em> it calls {@code
 * selectWord}, so nothing is selected yet at that point and reading the cursor there would always
 * return nothing.
 */
final class LongPressController {

  interface HostResolver {
    EditorHost resolve();
  }

  interface Listener {
    void onJsonCaptured(String json);

    void onLongPressRejected(String reason);
  }

  private static final long POLL_INTERVAL_MS = 750L;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final HostResolver hostResolver;
  private final Listener listener;
  private final Map<CodeEditor, SubscriptionReceipt<LongPressEvent>> receipts =
      new WeakHashMap<>();

  private CodeEditor attached;
  private boolean closed;

  private final Runnable pollTask =
      new Runnable() {
        @Override
        public void run() {
          if (closed) {
            return;
          }
          syncWithCurrentEditor();
          if (!closed) {
            main.postDelayed(this, POLL_INTERVAL_MS);
          }
        }
      };

  LongPressController(HostResolver hostResolver, Listener listener) {
    this.hostResolver = hostResolver;
    this.listener = listener;
  }

  void start() {
    syncWithCurrentEditor();
    main.postDelayed(pollTask, POLL_INTERVAL_MS);
  }

  /** Keeps exactly one subscription alive, pointing at the editor currently in use. */
  private void syncWithCurrentEditor() {
    CodeEditor current = null;
    EditorHost host = hostResolver.resolve();
    if (host != null) {
      Object rawEditor = host.getEditor();
      if (rawEditor instanceof CodeEditor) {
        current = (CodeEditor) rawEditor;
      }
    }
    if (current == attached) {
      return;
    }
    synchronized (receipts) {
      if (current == attached) {
        return;
      }
      detachLocked();
      if (current == null) {
        return;
      }
      attachLocked(current);
    }
  }

  private void attachLocked(CodeEditor editor) {
    SubscriptionReceipt<LongPressEvent> receipt =
        editor.subscribeEvent(LongPressEvent.class, this::onLongPress);
    receipts.put(editor, receipt);
    attached = editor;
  }

  private void detachLocked() {
    for (SubscriptionReceipt<LongPressEvent> receipt : receipts.values()) {
      receipt.unsubscribe();
    }
    receipts.clear();
    attached = null;
  }

  private void onLongPress(LongPressEvent event, Unsubscribe unsubscribe) {
    if (closed) {
      return;
    }
    int region = event.getMotionRegion();
    if (region == LongPressEvent.REGION_LINE_NUMBER || region == LongPressEvent.REGION_SIDE_ICON) {
      return;
    }

    CodeEditor editor = event.getEditor();
    Content text = editor == null ? null : editor.getText();
    if (text == null) {
      return;
    }

    int[] block = JsonBlockSelector.findEnclosingBlock(text, event.getIndex());
    if (block == null) {
      listener.onLongPressRejected("Long press inside a JSON object or array.");
      return;
    }

    int start = block[0];
    int end = block[1];
    if (start < 0 || end >= text.length() || start > end) {
      listener.onLongPressRejected("Long press inside a JSON object or array.");
      return;
    }

    String json = text.subSequence(start, end + 1).toString();
    if (!JsonCodeGenerator.isValidJson(json)) {
      listener.onLongPressRejected("That block is not valid JSON.");
      return;
    }

    // Only take over the gesture once there is something usable to hand to the panel.
    if (event.canIntercept()) {
      event.intercept(InterceptTarget.TARGET_EDITOR);
    }

    CharPosition startPosition = text.getIndexer().getCharPosition(start);
    CharPosition endPosition = text.getIndexer().getCharPosition(end);
    editor.setSelectionRegion(
        startPosition.line,
        startPosition.column,
        endPosition.line,
        endPosition.column,
        true,
        SelectionChangeEvent.CAUSE_LONG_PRESS);

    listener.onJsonCaptured(json);
  }

  void close() {
    closed = true;
    main.removeCallbacks(pollTask);
    synchronized (receipts) {
      detachLocked();
    }
  }
}
