package ir.ghostide.glsllsp;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

/**
 * Installs and runs glsl_analyzer inside the proot rootfs, and highlights shaders with the
 * TextMate grammar bundled in the plugin.
 *
 * <p>The setup action writes {@code assets/install-glsl-lsp.sh} to {@code /opt/glsl-lsp/install.sh}
 * with a single heredoc and executes it, so the installer on disk is exactly the readable script
 * shipped in the APK - the same thing a user gets by pasting it into a terminal. The script unpacks
 * the release zip into {@code /opt/glsl-lsp} and writes the {@code /usr/local/bin/glsl_analyzer}
 * launcher this plugin starts.
 *
 * <p>The extension list is the shader set the server understands: the five SPIR-V stages plus the
 * two platform-neutral suffixes that carry an in-file stage hint.
 */
public final class GlslLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-glsl-lsp.sh";

  /** The name the installer is known by inside the rootfs; the plugin manager runs it directly. */
  private static final String INSTALL_SCRIPT = "install.sh";

  private static final String INSTALL_DIR = "/opt/glsl-lsp";

  /** extension paired with the shader stage the server will infer from it. */
  private static final String[][] STAGES = {
    {"vert", "Vertex"},
    {"frag", "Fragment"},
    {"geom", "Geometry"},
    {"tesc", "Tessellation control"},
    {"tese", "Tessellation evaluation"},
    {"comp", "Compute"},
    {"glsl", "GLSL"},
    {"glsles", "GLSL ES"},
  };

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
            + "\n"
            + "cat > "
            + INSTALL_DIR
            + "/"
            + INSTALL_SCRIPT
            + " <<'GLSL_ASSET_EOF'\n"
            + body
            + "\nGLSL_ASSET_EOF\n"
            + "chmod +x "
            + INSTALL_DIR
            + "/"
            + INSTALL_SCRIPT
            + "\n"
            + INSTALL_DIR
            + "/"
            + INSTALL_SCRIPT
            + "\n";
    return List.of(
        new PluginSetupAction(
            "install-glsl-lsp",
            "Install GLSL LSP (glsl_analyzer)",
            command,
            "Installs glsl_analyzer, a static Rust language server for GLSL, into the rootfs and "
                + "writes its launcher. No toolchain, no SDK and no glslang build step: the binary "
                + "is self-contained. Re-running the action on the same version only refreshes the "
                + "launcher."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    String pluginId = context.getDescriptor().getId();

    GlslTextmateHost.create(androidContext, context.getLogger());
    GlslTextmateHost textmate = GlslTextmateHost.instance();
    textmate.loadAsync();

    List<GlslLspProvider> providers = new ArrayList<>(STAGES.length);
    for (String[] stage : STAGES) {
      providers.add(new GlslLspProvider(launcher, textmate, context.getLogger(), stage[0], stage[1]));
    }

    for (GlslLspProvider provider : providers) {
      Disposable registration =
          context
              .getExtensions()
              .register(
                  EditorExtensionPoints.LSP_SERVER_PROVIDER,
                  provider,
                  pluginId,
                  GlslLspProvider.PRIORITY);
      context.registerDisposable(registration);
    }

    Disposable runRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_ACTION_HANDLER,
                createRunFileHandler(new GlslRunner(context, launcher)),
                pluginId,
                0);
    context.registerDisposable(runRegistration);

    Disposable fileEvents =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT, (FileEventListener) this::onFileEvent, pluginId, 100);
    context.registerDisposable(fileEvents);

    context
        .getLogger()
        .info(
            providers.get(0).isInstalled()
                ? "glsl_analyzer found in rootfs"
                : "glsl_analyzer not installed yet; run the setup action from the Plugin Manager");
  }

  /**
   * Puts the bundled grammar on the editor the moment a shader is opened.
   *
   * <p>Waiting for the language server would be the obvious route and the wrong one: the server only
   * connects once the editor asks for it, and on a phone that can take longer than the user is
   * willing to look at uncoloured code. The provider declares the same scope, so this and the LSP
   * path agree on what the shader should look like.
   */
  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null || !isShader(path)) {
      return;
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor editor)) {
      return;
    }
    GlslTextmateHost host = GlslTextmateHost.instance();
    if (host != null) {
      host.setWhenReady(editor, GlslTextmateHost.GLSL_SCOPE);
    }
  }

  private static boolean isShader(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    for (String[] stage : STAGES) {
      if (lower.endsWith("." + stage[0])) {
        return true;
      }
    }
    return false;
  }

  /**
   * Owns the editor's run command for shader files, so the FAB compiles the open shader instead of
   * handing it to a runner that does not know what a {@code .frag} is. Everything the handler does
   * not recognise is passed on, which keeps the host's own behaviour for every other file.
   */
  private static EditorActionHandler createRunFileHandler(GlslRunner runner) {
    return new EditorActionHandler() {
      @Override
      public String getCommandId() {
        return "ghostide.runFile";
      }

      @Override
      public boolean execute(Object editor, String command, List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
          return false;
        }
        String filePath = String.valueOf(arguments.get(0));
        if (!runner.isSupported(filePath)) {
          return false;
        }
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
    try (InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}
