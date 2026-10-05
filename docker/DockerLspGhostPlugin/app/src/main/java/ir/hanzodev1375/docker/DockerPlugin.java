package ir.hanzodev1375.docker;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

public final class DockerPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-docker-lsp.sh";
  /** Files written next to install.sh before it runs (name inside the assets folder). */
  private static final String[] EXTRA_ASSETS = {"docker-shim.js"};
  private static final String SETUP_DIR = "/opt/docker-lsp";
  private static final long[] APPLY_DELAYS_MS = {0L, 250L, 700L, 1500L, 3000L};

  private PluginContext context;

  static boolean isDockerfile(File f) {
    String n = f.getName().toLowerCase(Locale.ROOT);
    return n.equals("dockerfile") || n.equals("containerfile") || n.startsWith("dockerfile.")
        || n.startsWith("containerfile.") || n.endsWith(".dockerfile") || n.endsWith(".containerfile");
  }

  static boolean isCompose(File f) {
    return f.getName().toLowerCase(Locale.ROOT).matches("^(?:docker-)?compose(?:\\.[\\w.-]+)?\\.ya?ml$");
  }

  /** Compose files are YAML: the host's own YAML grammar colours them. */
  static String scopeFor(File f) {
    return isDockerfile(f) ? "source.dockerfile" : null;
  }

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String install = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (install.isBlank()) {
      return List.of();
    }
    StringBuilder command = new StringBuilder("mkdir -p " + SETUP_DIR + "\n");
    for (int i = 0; i < EXTRA_ASSETS.length; i++) {
      String body = readAsset(context, EXTRA_ASSETS[i]);
      if (body.isBlank()) {
        return List.of();
      }
      String delimiter = "GHOST_ASSET_" + i + "_EOF";
      command
          .append("cat > ").append(SETUP_DIR).append('/').append(EXTRA_ASSETS[i])
          .append(" <<'").append(delimiter).append("'\n")
          .append(body).append('\n').append(delimiter).append('\n');
    }
    command
        .append("cat > ").append(SETUP_DIR).append("/install.sh <<'INSTALL_EOF'\n")
        .append(install)
        .append("\nINSTALL_EOF\nchmod +x ").append(SETUP_DIR).append("/install.sh\n")
        .append(SETUP_DIR).append("/install.sh\n");
    return List.of(
        new PluginSetupAction(
            "install-docker-language-servers", "Install Docker language servers (Dockerfile + Compose)", command.toString(), "Installs dockerfile-language-server-nodejs and @microsoft/compose-language-service inside the proot rootfs (Node.js 18+) plus a small shim that sets the Compose languageId."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    DockerGrammarHost.create(androidContext, context.getLogger());
    DockerGrammarHost.instance().loadAsync();

    Disposable dockerfile =
        context.getExtensions().register(
            EditorExtensionPoints.LSP_SERVER_PROVIDER,
            new DockerLspProvider(
                "ir.hanzodev1375.docker.dockerfile", "Dockerfile Language Server", 450,
                Set.of("dockerfile"), DockerPlugin::isDockerfile, "source.dockerfile",
                "/usr/local/bin/ghost-dockerfile-ls", List.of("--stdio"), launcher, context.getLogger()),
            context.getDescriptor().getId(), 450);
    context.registerDisposable(dockerfile);

    Disposable compose =
        context.getExtensions().register(
            EditorExtensionPoints.LSP_SERVER_PROVIDER,
            new DockerLspProvider(
                "ir.hanzodev1375.docker.compose", "Docker Compose Language Server", 450,
                Set.of("yml", "yaml"), DockerPlugin::isCompose, null,
                "/usr/local/bin/ghost-compose-ls", List.of("--stdio"), launcher, context.getLogger()),
            context.getDescriptor().getId(), 450);
    context.registerDisposable(compose);

    Disposable fileEvents =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                (FileEventListener) this::onFileEvent,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(fileEvents);

    context.getLogger().info("Docker language support registered");
  }

  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED || event.path() == null) {
      return;
    }
    final String path = event.path();
    final String scope = scopeFor(new File(path));
    if (scope == null) {
      return;
    }
    android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    for (long delay : APPLY_DELAYS_MS) {
      main.postDelayed(() -> applyGrammar(path, scope), delay);
    }
  }

  /** OPENED fires before the new tab's editor is current: retry until the right file is open. */
  private void applyGrammar(String path, String scope) {
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    File open = editorHost.getOpenFile();
    if (open != null && !open.getAbsolutePath().equals(new File(path).getAbsolutePath())) {
      return;
    }
    Object raw = editorHost.getEditor();
    if (!(raw instanceof CodeEditor editor)) {
      return;
    }
    DockerGrammarHost host = DockerGrammarHost.instance();
    if (host != null) {
      host.setWhenReady(editor, scope);
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
