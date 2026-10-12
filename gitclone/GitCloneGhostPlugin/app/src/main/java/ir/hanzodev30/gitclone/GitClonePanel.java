package ir.hanzodev30.gitclone;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import ir.hanzodev1375.ghostide.ide.ui.api.FilePanel;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;

final class GitClonePanel implements FilePanel {

  private final GitClonePanelView view;
  private final Context context;

  GitClonePanel(GitClonePanelView view, Context context) {
    this.view = view;
    this.context = context;
    setState(PluginStateMod.BOTTOMSHEETDIALOG);
  }

  @Override
  public PluginStateMod getState() {
    return PluginStateMod.BOTTOMSHEETDIALOG;
  }

  @Override
  public String getId() {
    return "ir.hanzodev30.gitclone.panel";
  }

  @Override
  public String getTitle() {
    return context.getString(R.string.gitclone_app_name);
  }

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
