package ir.ghostides.volume_cursor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ir.hanzodev1375.ghostide.codeeditors.IdeEditor;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEventListener;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.CoreServices;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;
import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

public final class VolumeCursorPlugin implements GhostPlugin {

  private static final long RECONNECT_INTERVAL_MS = 500L;

  private final Handler main = new Handler(Looper.getMainLooper());

  @Nullable private PluginContext context;
  @Nullable private VolumeCursorController controller;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of();
  }

  @Override
  public void activate(@NonNull PluginContext context) {
    this.context = context;
    PluginStorage storage = context.getServices().require(CoreServices.PLUGIN_STORAGE);
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    VolumeCursorSettings settings = new VolumeCursorSettings(storage);
    controller = new VolumeCursorController(settings);
    context.registerDisposable(
        context.getExtensions().register(
            PluginUiExtensionPoints.PLUGIN_SCREEN,
            new VolumeCursorSettingsScreen(settings, androidContext),
            context.getDescriptor().getId(),
            0));
    context
        .getExtensions()
        .register(
            IdeEvents.FILE_EVENT,
            (FileEventListener) this::onFileEvent,
            context.getDescriptor().getId(),
            0);
    attachCurrentEditor();
  }

  @Override
  public void deactivate() {
    if (controller != null) {
      controller.detach();
      controller = null;
    }
    context = null;
  }

  private void onFileEvent(FileEvent event) {
    if (controller == null) {
      return;
    }
    if (event.type() == FileEvent.Type.OPENED) {
      main.post(this::attachCurrentEditor);
    }
  }

  private void attachCurrentEditor() {
    VolumeCursorController controller = this.controller;
    PluginContext context = this.context;
    if (controller == null || context == null) {
      return;
    }
    try {
      EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
      Object raw = editorHost.getEditor();
      if (raw instanceof IdeEditor ide) {
        controller.attach(ide);
      }
    } catch (Exception ignored) {
    }
    main.postDelayed(this::attachCurrentEditor, RECONNECT_INTERVAL_MS);
  }
}