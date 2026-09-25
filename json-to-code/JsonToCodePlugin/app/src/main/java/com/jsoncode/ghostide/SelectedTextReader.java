package com.jsoncode.ghostide;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;

import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;

final class SelectedTextReader {

  private SelectedTextReader() {}

  static String read(EditorHost host) {
    if (host == null) {
      return "";
    }
    Object rawEditor = host.getEditor();
    if (rawEditor instanceof CodeEditor) {
      String selected = read((CodeEditor) rawEditor);
      if (!selected.trim().isEmpty()) {
        return selected;
      }
    }
    String text = host.getEditorText();
    return text == null ? "" : text;
  }

  static String read(CodeEditor editor) {
    if (editor == null) {
      return "";
    }
    Cursor cursor = editor.getCursor();
    if (cursor == null || !cursor.isSelected()) {
      return editor.getText().toString();
    }
    int start = Math.min(cursor.getLeft(), cursor.getRight());
    int end = Math.max(cursor.getLeft(), cursor.getRight());
    if (start < 0 || end > editor.getText().length() || start >= end) {
      return "";
    }
    return editor.getText().subSequence(start, end).toString();
  }
}
