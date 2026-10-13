package ir.hanzodev1375.jadx;

import ir.hanzodev1375.ghostide.ide.ui.api.FileClickContext;
import ir.hanzodev1375.ghostide.ide.ui.api.FileClickHandler;

import java.util.Locale;

/** Claims taps on {@code .dex} files (when enabled in settings) and opens the class browser. */
final class DexClickHandler implements FileClickHandler {

  private final JadxSettings settings;
  private final JadxController controller;

  DexClickHandler(JadxSettings settings, JadxController controller) {
    this.settings = settings;
    this.controller = controller;
  }

  /** Runs on the main thread for every tap: name checks only, no disk access. */
  @Override
  public boolean supports(String path, String name, boolean isDirectory) {
    return !isDirectory
        && name != null
        && settings.isTapDex()
        && name.toLowerCase(Locale.ROOT).endsWith(".dex");
  }

  @Override
  public void onFileClick(FileClickContext click) {
    controller.onDexTap(click.context(), click.file());
  }
}
