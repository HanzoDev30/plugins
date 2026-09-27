package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The bottom sheet that carries the AndroidBuilder UI.
 *
 * <p>{@link #getLastPath()} deliberately stays {@code null}: the host asks every registered panel
 * which file the code runner should run, and an empty answer keeps the runner on the file the user
 * has open instead of hijacking it.
 *
 * <p>The project path is the opposite: it is never stored, it is read from the tab that is open
 * every time the panel appears. {@link #onFileEvent} is how a tab that opens while the panel is
 * already up reaches the field.
 */
final class AndroidBuilderPanel implements EditorPanel {

  private static final String PREFS = "androidbuilder";

  private final Context context;
  private final PluginContext plugin;
  private AndroidBuilderView view;

  AndroidBuilderPanel(Context context, PluginContext plugin) {
    this.context = context;
    this.plugin = plugin;
    setState(PluginStateMod.BOTTOMSHEETDIALOG);
  }

  @Override
  public String getId() {
    return "ir.ghostide.androidbuilder.panel";
  }

  @Override
  public String getTitle() {
    return "AndroidBuilder for ir";
  }

  /**
   * The host caches one view per panel id, but that cache lives on the host and the host is rebuilt
   * whenever the editor Activity is (rotation, theme change, a second editor window). This panel is
   * a process-wide singleton, so the root it hands out can still be attached to the previous host's
   * wrapper, and {@code ViewGroup.addView} throws on a child that already has a parent — the whole
   * app goes down. Handing the view back detached makes the panel safe to open from any host, any
   * number of times.
   */
  @Override
  public View createView() {
    if (view == null) {
      SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      view = new AndroidBuilderView(context, plugin, preferences);
    } else {
      view.refresh();
    }
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

  /**
   * A tab opened, closed, renamed or was deleted: the project field follows whatever is in front of
   * the user now. A panel that was never opened has no field to move, so there is nothing to do.
   */
  void onFileEvent(FileEvent event) {
    AndroidBuilderView view = this.view;
    if (view != null) {
      view.onTabChanged();
    }
  }
}
