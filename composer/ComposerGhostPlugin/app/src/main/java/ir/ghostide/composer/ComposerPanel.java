package ir.ghostide.composer;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;

/**
 * The glass bottom sheet that carries the Composer UI.
 *
 * <p>{@link #getLastPath()} deliberately stays {@code null}: the host asks every registered panel
 * which file the code runner should run, and an empty answer keeps the runner on whatever the user
 * has open instead of hijacking it.
 */
final class ComposerPanel implements EditorPanel {

  private final ComposerPanelView view;

  ComposerPanel(ComposerPanelView view) {
    this.view = view;
    setState(PluginStateMod.BOTTOMSHEETDIALOG);
  }

  @Override
  public PluginStateMod getState() {
    return PluginStateMod.BOTTOMSHEETDIALOG;
  }

  @Override
  public String getId() {
    return "ir.ghostide.composer.panel";
  }

  @Override
  public String getTitle() {
    return "Composer";
  }

  /**
   * The host keeps one view per panel id, but that cache lives on the host, and the host is rebuilt
   * whenever the editor Activity is. This panel is a process-wide singleton, so the root it hands
   * out can still be attached to the previous host's wrapper: every {@code ViewGroup.addView} of a
   * child that already has a parent throws, and the whole app goes down. Handing the view back
   * detached makes the panel safe to open from any host, any number of times.
   */
  @Override
  public View createView() {
    View root = view.getRoot();
    ViewParent parent = root.getParent();
    if (parent instanceof ViewGroup) {
      ((ViewGroup) parent).removeView(root);
    }
    root.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return root;
  }

  @Override
  public String getLastPath() {
    return null;
  }
}
