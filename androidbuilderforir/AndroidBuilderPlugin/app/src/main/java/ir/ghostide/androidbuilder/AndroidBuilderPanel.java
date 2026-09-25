package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The bottom sheet that carries the AndroidBuilder UI.
 *
 * <p>{@link #getLastPath()} deliberately stays {@code null}: the host asks every registered panel
 * which file the code runner should run, and an empty answer keeps the runner on the file the user
 * has open instead of hijacking it.
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

  @Override
  public View createView() {
    if (view == null) {
      SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      view = new AndroidBuilderView(context, plugin, preferences);
    } else {
      view.refresh();
    }
    return view.getRoot();
  }

  @Override
  public String getLastPath() {
    return null;
  }
}
