package com.jsoncode.ghostide;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;

import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Reads the text the user explicitly selected in the editor.
 *
 * <p>This class never falls back to the whole document. An empty result means "nothing is
 * selected", and callers must treat that as a hard stop instead of generating a model from the
 * entire file.
 */
final class SelectedTextReader {

  private SelectedTextReader() {}

  static String read(EditorHost host) {
    if (host == null) {
      return "";
    }
    Object rawEditor = host.getEditor();
    if (rawEditor instanceof CodeEditor) {
      return read((CodeEditor) rawEditor);
    }
    return "";
  }

  static String read(CodeEditor editor) {
    if (editor == null) {
      return "";
    }
    Cursor cursor = editor.getCursor();
    if (cursor == null || !cursor.isSelected()) {
      return "";
    }
    int start = Math.min(cursor.getLeft(), cursor.getRight());
    int end = Math.max(cursor.getLeft(), cursor.getRight());
    if (start < 0 || end > editor.getText().length() || start >= end) {
      return "";
    }
    return editor.getText().subSequence(start, end).toString();
  }

  static boolean hasSelection(EditorHost host) {
    return !read(host).isEmpty();
  }
}
