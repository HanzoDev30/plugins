package ir.hanzodev1375.react;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
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
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIconState;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginIcons;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.CoreExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * React JS/TS support: a TypeScript-backed language server with JSX awareness, bundled React
 * TextMate grammars and a runner that starts a React project (or a single component) in the IDE
 * terminal.
 */
public final class ReactPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-react-lsp.sh";
  private static final String INSTALL_DIR = "/opt/react-lsp";

  private PluginContext context;
  private ReactRunner runner;

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
            "install-react-language-server",
            "Install React language server (TypeScript)",
            command,
            "Installs Node.js, typescript, typescript-language-server and React type packages "
                + "inside the proot rootfs. Provides completion, diagnostics, hover, JSX support "
                + "and go-to-definition for .tsx/.jsx/.ts/.js files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    String owner = context.getDescriptor().getId();

    ReactTextmateHost.create(androidContext, context.getLogger());
    ReactTextmateHost host = ReactTextmateHost.instance();
    host.loadAsync();

    Disposable lsp =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                new ReactLspProvider(launcher, context.getLogger()),
                owner,
                ReactLspProvider.PRIORITY);
    context.registerDisposable(lsp);

    Disposable events =
        context
            .getExtensions()
            .register(IdeEvents.FILE_EVENT, (FileEventListener) this::onFileEvent, owner, 100);
    context.registerDisposable(events);

    runner = new ReactRunner(context);
    Disposable command =
        context
            .getExtensions()
            .register(
                CoreExtensionPoints.PLUGIN_COMMAND, new ReactRunCommand(runner), owner, 0);
    context.registerDisposable(command);

    PluginIcons.setPluginShowState(
        context,
        PluginIconState.Editor,
        "Run React",
        v -> {
          if (runner != null) {
            runner.run();
          }
        },
        loadAssetIcon(androidContext, "react.png"));

    context.getLogger().info("React support activated");
  }

  @Override
  public void deactivate() {
    runner = null;
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null) {
      return;
    }
    File file = new File(path);
    String extension = ReactProject.extension(file.getName());
    String scope = ReactTextmateHost.scopeForExtension(extension);
    if (scope == null) {
      return;
    }
    // Plain .ts/.js only get React highlighting inside a React project, so the built-in
    // TypeScript grammar keeps working everywhere else.
    if (!"tsx".equals(extension) && !"jsx".equals(extension)) {
      if (ReactProject.findRoot(file, null) == null) {
        return;
      }
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor)) {
      return;
    }
    ReactTextmateHost host = ReactTextmateHost.instance();
    if (host != null) {
      host.setWhenReady((CodeEditor) rawEditor, scope);
    }
  }

  private static Drawable loadAssetIcon(Context context, String assetName) {
    try (InputStream in = context.getAssets().open(assetName)) {
      return new BitmapDrawable(context.getResources(), BitmapFactory.decodeStream(in));
    } catch (IOException | RuntimeException e) {
      return null;
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
