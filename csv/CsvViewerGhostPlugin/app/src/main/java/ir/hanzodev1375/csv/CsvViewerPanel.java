package ir.hanzodev1375.csv;

import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginStateMod;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/** Editor panel that shows the open CSV/TSV file as a table. */
public final class CsvViewerPanel implements EditorPanel {

  private final PluginContext context;
  private CsvViewerView view;

  CsvViewerPanel(PluginContext context) {
    this.context = context;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.csv.panel";
  }

  @Override
  public String getTitle() {
    return "CSV Viewer";
  }

  @Override
  public View createView() {
    // The host may call this on every show and add the result to a fresh wrapper, so reusing a
    // cached instance crashes with "specified child already has a parent". Always build a new view.
    EditorHost host = context.getServices().require(IdeHostServices.EDITOR_HOST);
    CsvViewerView fresh = new CsvViewerView(host.getContext(), host, context.getLogger());
    view = fresh;
    return fresh;
  }

  /** A table needs width: show it as a dialog instead of the narrow default side sheet. */
  @Override
  public PluginStateMod getState() {
    return PluginStateMod.DIALOG;
  }

  /** Called on the main thread when the open file is opened/saved. */
  void refresh() {
    CsvViewerView v = view;
    if (v != null && v.isAttachedToWindow()) {
      v.reload();
    }
  }
}
