package ir.ghostides.thememaker;

import android.content.Context;
import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorPanel;

/** The Theme Maker {@link EditorPanel}, hosted as a side sheet inside the editor screen. */
public final class ThemeMakerPanel implements EditorPanel {

  private final Context pluginContext;

  public ThemeMakerPanel(Context pluginContext) {
    this.pluginContext = pluginContext;
  }

  @Override
  public String getId() {
    return "ir.ghostides.thememaker.panel";
  }

  @Override
  public String getTitle() {
    return "Theme Maker";
  }

  @Override
  public View createView() {
    return new ThemeMakerView(pluginContext).getRoot();
  }
}
