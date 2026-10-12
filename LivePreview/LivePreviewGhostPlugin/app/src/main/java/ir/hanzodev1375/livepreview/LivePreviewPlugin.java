package ir.hanzodev1375.livepreview;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIconState;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIcons;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.PanelHostService;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.CoreExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.CoreServices;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

import java.io.IOException;
import java.io.InputStream;

/** Entry class of the Live Preview plugin. */
public final class LivePreviewPlugin implements GhostPlugin {

  private LivePreviewPanel panel;

  @Override
  public void activate(PluginContext context) {
    String owner = context.getDescriptor().getId();

    PluginStorage storage = context.getServices().require(CoreServices.PLUGIN_STORAGE);
    LivePreviewSettings settings = new LivePreviewSettings(storage);
    panel = new LivePreviewPanel(context, settings);

    // 1) the preview panel
    Disposable panelReg =
        context
            .getExtensions()
            .register(PluginUiExtensionPoints.EDITOR_PANEL, panel, owner, 0);
    context.registerDisposable(panelReg);

    // 2) reload when a file is saved (listener may run on any thread)
    Disposable eventReg =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                event -> {
                  if ("SAVED".equals(event.type().name())) {
                    panel.onFileSaved(event.path());
                  }
                },
                owner,
                0);
    context.registerDisposable(eventReg);

    // 3) WebView preferences shown in the IDE settings screen
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    Disposable settingsReg =
        context
            .getExtensions()
            .register(
                CoreExtensionPoints.PLUGIN_SETTINGS,
                new LivePreviewSettingsContributor(androidContext, settings, panel),
                owner,
                0);
    context.registerDisposable(settingsReg);

    // 4) persistent toolbar icon using the new PluginIcons API. It shows next to the IDE's own
    // toolbar icons in the EDITOR only (PluginIconState.Editor). Tapping it runs the panel for real
    // through the host's PANEL_HOST service (no popup, no toast). The bitmap is loaded straight
    // from the plugin assets folder.
    Drawable assetIcon = loadAssetIcon(androidContext, "toolbar_icon.png");
    PluginIcons.setPluginShowState(
        context,
        PluginIconState.Editor,
        "Live Preview",
        v -> openPanel(context),
        assetIcon);

    context.getLogger().info("Live Preview activated");
  }

  @Override
  public void deactivate() {
    if (panel != null) {
      panel.destroy();
      panel = null;
    }
  }

  private void openPanel(PluginContext context) {
    if (panel == null) {
      return;
    }
    PanelHostService host = context.getServices().get(IdeHostServices.PANEL_HOST);
    if (host != null) {
      host.showPanel(panel);
    } else {
      toast(context, "Live Preview: host panel support missing, use the plugin popup");
    }
  }

  private static Drawable loadAssetIcon(Context context, String assetName) {
    try (InputStream in = context.getAssets().open(assetName)) {
      return new BitmapDrawable(context.getResources(), BitmapFactory.decodeStream(in));
    } catch (IOException e) {
      return null;
    }
  }

  private static void toast(PluginContext context, String message) {
    UiFeedbackHost ui = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (ui != null) {
      ui.toast(message);
    }
  }
}
