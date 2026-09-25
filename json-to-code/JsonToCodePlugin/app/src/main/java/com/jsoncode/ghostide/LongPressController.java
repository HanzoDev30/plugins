package com.jsoncode.ghostide;

import android.content.Context;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;

import java.util.HashMap;
import java.util.Map;

import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.widget.CodeEditor;

final class LongPressController {

  private final Context context;
  private final Map<CodeEditor, SubscriptionReceipt<LongPressEvent>> receipts = new HashMap<>();
  private boolean closed;

  LongPressController(Context context) {
    this.context = context;
  }

  void attach(EditorHost host) {
    if (closed || host == null) {
      return;
    }
    Object rawEditor = host.getEditor();
    if (!(rawEditor instanceof CodeEditor)) {
      return;
    }
    CodeEditor editor = (CodeEditor) rawEditor;
    synchronized (receipts) {
      if (receipts.containsKey(editor)) {
        return;
      }
    }

    SubscriptionReceipt<LongPressEvent> receipt =
        editor.subscribeEvent(
            LongPressEvent.class,
            (event, unsubscribe) -> {
              if (event.getMotionRegion() != LongPressEvent.REGION_TEXT) {
                return;
              }
              CodeEditor target = event.getEditor();
              target.postDelayed(
                  () -> {
                    if (closed) {
                      return;
                    }
                    String json = SelectedTextReader.read(target);
                    if (JsonCodeGenerator.isValidJson(json)) {
                       JsonToCodeView.showFlow(host, json);
                    }
                  },
                  32L);
            });

    synchronized (receipts) {
      if (closed) {
        receipt.unsubscribe();
      } else {
        receipts.put(editor, receipt);
      }
    }
  }

  void close() {
    closed = true;
    synchronized (receipts) {
      for (SubscriptionReceipt<LongPressEvent> receipt : receipts.values()) {
        receipt.unsubscribe();
      }
      receipts.clear();
    }
  }
}
