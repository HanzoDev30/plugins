package ir.hanzodev30.nodeui;

import ir.theme.M3Theme;

final class Palette {

  private Palette() {}

  static int col(Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  static int surface() {
    return col(M3Theme.surface(), 0xFF1C1B1F);
  }

  static int surfaceContainer() {
    return col(M3Theme.surfaceContainer(), 0xFF211F26);
  }

  static int onSurface() {
    return col(M3Theme.onSurface(), 0xFFE6E1E5);
  }

  static int onSurfaceVariant() {
    return col(M3Theme.onSurfaceVariant(), 0xFFCAC4D0);
  }

  static int primary() {
    return col(M3Theme.primary(), 0xFF6750A4);
  }

  static int onPrimary() {
    return col(M3Theme.onPrimary(), 0xFFFFFFFF);
  }

  static int secondary() {
    return col(M3Theme.secondary(), 0xFF625B71);
  }

  static int outlineVariant() {
    return col(M3Theme.outlineVariant(), 0xFF49454F);
  }

  static int error() {
    return col(M3Theme.error(), 0xFFB3261E);
  }

  static int success() {
    return 0xFF34D399;
  }

  static int warning() {
    return 0xFFFBBF24;
  }
}
