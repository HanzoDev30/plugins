package ir.hanzodev1375.astro;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEventListener;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class AstroPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-astro-lsp.sh";
  private static final String INSTALL_DIR = "/opt/astro-lsp";
  private static final String ASTRO_EXTENSION = ".astro";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (body.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p "
            + INSTALL_DIR
            + "\ncat > "
            + INSTALL_DIR
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\nchmod +x "
            + INSTALL_DIR
            + "/install.sh\n"
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-astro-language-server",
            "Install Astro language server (@astrojs/language-server)",
            command,
            "Installs Node.js, @astrojs/language-server and typescript inside the proot rootfs. "
                + "Provides completion, diagnostics, hover, inlay hints and go-to-definition for "
                + ".astro and .mdx files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    AstroTextmateHost.create(androidContext, context.getLogger());
    AstroTextmateHost host = AstroTextmateHost.instance();
    host.loadAsync();

    Disposable lsp =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                new AstroLspProvider(launcher, host, context.getLogger()),
                context.getDescriptor().getId(),
                350);
    context.registerDisposable(lsp);

    Disposable fileEvents =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                (FileEventListener) this::onFileEvent,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(fileEvents);

    context.getLogger().info("Astro language support registered");
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(ASTRO_EXTENSION)) {
      return;
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor editor)) {
      return;
    }
    AstroTextmateHost host = AstroTextmateHost.instance();
    if (host != null) {
      host.setWhenReady(editor, AstroTextmateHost.ASTRO_SCOPE);
    }
  }

  private static String readAsset(PluginContext context, String name) {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}