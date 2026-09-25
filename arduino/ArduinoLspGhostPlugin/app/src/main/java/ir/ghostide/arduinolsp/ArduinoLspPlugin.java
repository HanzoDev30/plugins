package ir.ghostide.arduinolsp;

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

public final class ArduinoLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-arduino-lsp.sh";
  private static final String INSTALL_DIR = "/opt/arduino-lsp";

  private PluginContext context;
  private List<PluginSetupAction> setupActions;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (setupActions == null) {
      if (context == null) {
        return List.of();
      }
      setupActions = createSetupActions();
    }
    return setupActions;
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    getSetupActions();
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    ArduinoTextmateHost.create(androidContext, context.getLogger());
    ArduinoTextmateHost host = ArduinoTextmateHost.instance();
    host.loadAsync();

    ArduinoLspProvider provider = new ArduinoLspProvider(launcher, host);
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

    ArduinoRunner runner = new ArduinoRunner(context);
    Disposable runnerRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_ACTION_HANDLER,
                createRunFileHandler(runner),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(runnerRegistration);

    context.getLogger().info("Arduino language support registered");
  }

  private List<PluginSetupAction> createSetupActions() {
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
            + "/bin/sh "
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-arduino-language-server",
            "Install Arduino toolchain (arduino-cli + arduino-language-server)",
            command,
            "Installs arduino-cli and the Arduino language server inside the proot rootfs, "
                + "bootstraps the AVR core, and provides completion, diagnostics, hover, "
                + "go-to-definition and references for .ino/.pde sketches."));
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null || !isArduinoFile(path)) {
      return;
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor)) {
      return;
    }
    ArduinoTextmateHost host = ArduinoTextmateHost.instance();
    if (host != null) {
      host.setWhenReady((CodeEditor) rawEditor, ArduinoTextmateHost.ARDUINO_SCOPE);
    }
  }

  private boolean isArduinoFile(String path) {
    String name = path.toLowerCase(Locale.ROOT);
    return name.endsWith(".ino") || name.endsWith(".pde");
  }

  private EditorActionHandler createRunFileHandler(ArduinoRunner runner) {
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