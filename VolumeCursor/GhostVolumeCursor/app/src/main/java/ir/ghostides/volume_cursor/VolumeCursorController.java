package ir.ghostides.volume_cursor;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashSet;
import java.util.Set;

import io.github.rosemoe.sora.event.EditorKeyEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.SelectionMovement;

import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;

public final class VolumeCursorController {

  private static final String TAG = "VolumeCursor";
  private final VolumeCursorSettings settings;
  private final Set<Integer> pressedVolumeButtons = new HashSet<>();
  private IdeEditor editor;
  private SubscriptionReceipt<EditorKeyEvent> receipt;
  private CharPosition previousPosition;

  public VolumeCursorController(VolumeCursorSettings settings) {
    this.settings = settings;
  }

  public void attach(@NonNull IdeEditor editor) {
    Log.d(TAG, "attach: " + editor + " (editor=" + this.editor + ")");
    if (this.editor == editor) {
      return;
    }
    detach();
    this.editor = editor;
    editor.requestFocus();
    receipt = editor.subscribeAlways(EditorKeyEvent.class, this::onKeyEvent);
  }

  public void detach() {
    if (receipt != null) {
      receipt.unsubscribe();
      receipt = null;
    }
    editor = null;
    pressedVolumeButtons.clear();
    previousPosition = null;
  }

  private void onKeyEvent(EditorKeyEvent event) {
    IdeEditor editor = this.editor;
    if (editor == null) {
      return;
    }
    int keyCode = event.getKeyCode();
    if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
      return;
    }
    Log.d(TAG, "volume key: code=" + keyCode + " type=" + event.getEventType());
    if (settings.onlyKeyboardVisible() && !isKeyboardVisible(editor)) {
      Log.d(TAG, "ignored: keyboard not visible");
      return;
    }

    if (event.getEventType() != EditorKeyEvent.Type.DOWN) {
      pressedVolumeButtons.remove(keyCode);
      return;
    }

    pressedVolumeButtons.add(keyCode);

    if (pressedVolumeButtons.size() == 2) {
      event.interceptAndSetResult(true);
      if (previousPosition != null) {
        editor.setSelection(previousPosition.line, previousPosition.column);
      }
      String action = settings.bothButtonAction();
      if ("word".equals(action)) {
        editor.selectCurrentWord();
      } else if ("all".equals(action)) {
        editor.selectAll();
      }
      return;
    }

    event.interceptAndSetResult(true);

    boolean wordMovement = "word".equals(settings.movementMode());
    boolean horizontal = settings.cursorDirection().startsWith("horizontal");
    boolean reversed = settings.cursorDirection().endsWith("_reverse");

    SelectionMovement movement;
    if (horizontal && !reversed) {
      movement = horizontalMovement(event, wordMovement, false);
    } else if (horizontal) {
      movement = horizontalMovement(event, wordMovement, true);
    } else if (!reversed) {
      movement = verticalMovement(event, false);
    } else {
      movement = verticalMovement(event, true);
    }

    CharPosition before = editor.getCursor().left();
    CharPosition after = movement.getPositionAfterMovement(editor, before);
    if (horizontal && !settings.wrapLines() && after.line != before.line) {
      previousPosition = before;
      editor.moveOrExtendSelection(
          lineBoundaryMovement(event, reversed), editor.getCursor().isSelected());
      vibrate(editor);
      return;
    }

    previousPosition = before;
    editor.moveOrExtendSelection(movement, editor.getCursor().isSelected());
    vibrate(editor);
  }

  private static SelectionMovement horizontalMovement(
      EditorKeyEvent event, boolean word, boolean reverse) {
    boolean isMoveLeft =
        (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && !reverse)
            || (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && reverse);
    if (isMoveLeft && word) {
      return SelectionMovement.PREVIOUS_WORD_BOUNDARY;
    }
    if (isMoveLeft) {
      return SelectionMovement.LEFT;
    }
    if (word) {
      return SelectionMovement.NEXT_WORD_BOUNDARY;
    }
    return SelectionMovement.RIGHT;
  }

  private static SelectionMovement verticalMovement(EditorKeyEvent event, boolean reverse) {
    boolean isMoveUp =
        (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && !reverse)
            || (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && reverse);
    return isMoveUp ? SelectionMovement.UP : SelectionMovement.DOWN;
  }

  private static SelectionMovement lineBoundaryMovement(EditorKeyEvent event, boolean reverse) {
    boolean isMoveLeft =
        (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && !reverse)
            || (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && reverse);
    return isMoveLeft ? SelectionMovement.LINE_START : SelectionMovement.LINE_END;
  }

  private static boolean isKeyboardVisible(View view) {
    if (view.getRootWindowInsets() == null) {
      return false;
    }
    WindowInsetsCompat insets =
        WindowInsetsCompat.toWindowInsetsCompat(view.getRootWindowInsets(), view);
    return insets.isVisible(WindowInsetsCompat.Type.ime());
  }

  private void vibrate(@Nullable View view) {
    if (!settings.hapticFeedback()) {
      return;
    }
    try {
      Vibrator vibrator = (Vibrator) view.getContext().getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator == null || !vibrator.hasVibrator()) {
        return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
      } else {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
      }
    } catch (Exception ignored) {
    }
  }
}
