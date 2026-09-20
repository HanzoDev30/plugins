package ir.ghostides.volume_cursor;

import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

public final class VolumeCursorSettings {

  private final PluginStorage storage;

  public VolumeCursorSettings(PluginStorage storage) {
    this.storage = storage;
  }

  public String cursorDirection() {
    return storage.getString("volume_cursor.direction", "horizontal");
  }

  public void setCursorDirection(String direction) {
    storage.putString("volume_cursor.direction", direction);
  }

  public String movementMode() {
    return storage.getString("volume_cursor.movement_mode", "character");
  }

  public void setMovementMode(String mode) {
    storage.putString("volume_cursor.movement_mode", mode);
  }

  public boolean wrapLines() {
    return storage.getBoolean("volume_cursor.wrap_lines", true);
  }

  public void setWrapLines(boolean wrapLines) {
    storage.putBoolean("volume_cursor.wrap_lines", wrapLines);
  }

  public boolean onlyKeyboardVisible() {
    return storage.getBoolean("volume_cursor.only_keyboard_visible", false);
  }

  public void setOnlyKeyboardVisible(boolean onlyKeyboardVisible) {
    storage.putBoolean("volume_cursor.only_keyboard_visible", onlyKeyboardVisible);
  }

  public boolean hapticFeedback() {
    return storage.getBoolean("volume_cursor.haptic_feedback", true);
  }

  public void setHapticFeedback(boolean hapticFeedback) {
    storage.putBoolean("volume_cursor.haptic_feedback", hapticFeedback);
  }

  public String bothButtonAction() {
    return storage.getString("volume_cursor.both_button_action", "word");
  }

  public void setBothButtonAction(String action) {
    storage.putString("volume_cursor.both_button_action", action);
  }
}
