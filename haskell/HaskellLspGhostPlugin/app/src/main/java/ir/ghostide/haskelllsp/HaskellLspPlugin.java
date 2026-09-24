package ir.ghostide.haskelllsp;

import android.content.Context;

import java.util.List;
import java.util.Locale;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorActionHandler;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEvent;
import ir.hanzodev1375.ghostide.ide.ui.api.FileEventListener;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

public final class HaskellLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-haskell-lsp.sh";
  private static final String INSTALL_DIR = "/opt/haskell-lsp";

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
            "install-haskell-language-server",
            "Install Haskell language server (HLS)",
            command,
            "Installs GHCup and haskell-language-server inside the proot rootfs. "
                + "Provides completion, diagnostics, hover, formatting and symbols for .hs/.lhs files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    HaskellTextmateHost.create(androidContext, context.getLogger());
    HaskellTextmateHost host = HaskellTextmateHost.instance();
    host.loadAsync();

    HaskellLspProvider provider = new HaskellLspProvider(launcher, host);
    Disposable lsp =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                350);
    context.registerDisposable(lsp);

    Disposable file =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                (FileEventListener) this::onFileEvent,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(file);

    HaskellRunner runner = new HaskellRunner(context);
    Disposable runnerRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_ACTION_HANDLER,
                createRunFileHandler(runner),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(runnerRegistration);

    context.getLogger().info("Haskell language support registered");
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null || !isHaskellFile(path)) {
      return;
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor)) {
      return;
    }
    HaskellTextmateHost host = HaskellTextmateHost.instance();
    if (host != null) {
      host.setWhenReady((CodeEditor) rawEditor, HaskellTextmateHost.HASKELL_SCOPE);
    }
  }

  private boolean isHaskellFile(String path) {
    String name = path.toLowerCase(Locale.ROOT);
    return name.endsWith(".hs") || name.endsWith(".lhs");
  }

  private EditorActionHandler createRunFileHandler(HaskellRunner runner) {
    return new EditorActionHandler() {
      @Override
      public String getCommandId() {
        return "ghostide.runFile";
      }

      @Override
      public boolean execute(Object editor, String command, List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return false;
        String filePath = String.valueOf(arguments.get(0));
        if (!runner.isSupported(filePath)) return false;
        boolean asBottomSheet = true;
        if (arguments.size() > 1 && arguments.get(1) instanceof Boolean) {
          asBottomSheet = (Boolean) arguments.get(1);
        }
        runner.runFile(filePath, asBottomSheet);
        return true;
      }
    };
  }

  private static String readAsset(PluginContext context, String name) {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (java.io.InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}