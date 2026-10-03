package ir.ghostide.tomllsp;

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

/**
 * Installs and runs the Taplo TOML language server inside the proot rootfs, and highlights TOML
 * with the TextMate grammar bundled in the plugin.
 */
public final class TomlLspPlugin implements GhostPlugin {

  private static final String INSTALL_DIR = "/opt/toml-lsp";
  private static final String TOML_EXTENSION = ".toml";

  /**
   * The installer lives here as a constant rather than in an asset.
   *
   * <p>{@code getSetupActions()} can be called before {@link #activate}, so there is no
   * {@code PluginContext} yet and nothing can be read out of the APK - an asset-backed command
   * silently returns an empty list and the user gets no install option at all.
   */
  private static final String INSTALL_SCRIPT =
      "set -e\n"
          + "VERSION=0.10.0\n"
          + "case \"$(uname -m)\" in\n"
          + "  aarch64|arm64) ASSET=taplo-linux-aarch64.gz ;;\n"
          + "  armv7l|armhf)   ASSET=taplo-linux-armv7.gz ;;\n"
          + "  riscv64)        ASSET=taplo-linux-riscv64.gz ;;\n"
          + "  x86_64|amd64)   ASSET=taplo-linux-x86_64.gz ;;\n"
          + "  i386|i686)      ASSET=taplo-linux-x86.gz ;;\n"
          + "  *) echo \"unsupported architecture: $(uname -m)\"; exit 1 ;;\n"
          + "esac\n"
          + "URL=\"https://github.com/tamasfe/taplo/releases/download/${VERSION}/${ASSET}\"\n"
          + "mkdir -p /usr/local/bin\n"
          + "TMP=$(mktemp /tmp/taplo-XXXXXX.gz)\n"
          + "curl -fsSL -o \"$TMP\" \"$URL\"\n"
          + "gunzip -c \"$TMP\" > /usr/local/bin/taplo.new\n"
          + "chmod +x /usr/local/bin/taplo.new\n"
          + "/usr/local/bin/taplo.new --version\n"
          + "mv /usr/local/bin/taplo.new /usr/local/bin/taplo\n"
          + "rm -f \"$TMP\"\n"
          + "echo \"taplo installed at /usr/local/bin/taplo\"\n";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of(
        new PluginSetupAction(
            "install-toml-language-server",
            "Install TOML language server (taplo)",
            "mkdir -p "
                + INSTALL_DIR
                + "\n"
                + "cat > "
                + INSTALL_DIR
                + "/install.sh <<'INSTALL_EOF'\n"
                + INSTALL_SCRIPT
                + "INSTALL_EOF\n"
                + "chmod +x "
                + INSTALL_DIR
                + "/install.sh\n"
                + INSTALL_DIR
                + "/install.sh\n",
            "Downloads the official static taplo release (the npm package of the same name is built "
                + "without the language server) into the proot rootfs. Runs it as 'taplo lsp stdio' "
                + "for .toml files, giving completion, hover, diagnostics, symbols, folding, links, "
                + "rename, semantic tokens and formatting."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    TomlTextmateHost.create(androidContext, context.getLogger());
    TomlTextmateHost textmate = TomlTextmateHost.instance();
    textmate.loadAsync();

    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                new TomlLspProvider(launcher, textmate, context.getLogger()),
                context.getDescriptor().getId(),
                350);
    context.registerDisposable(registration);

    Disposable fileEvents =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT, (FileEventListener) this::onFileEvent, context.getDescriptor().getId(), 100);
    context.registerDisposable(fileEvents);

    context
        .getLogger()
        .info(
            "TOML language support registered ("
                + (launcher.isInstalled("/usr/local/bin/taplo")
                    ? "taplo found in rootfs"
                    : "taplo not installed yet; run the setup action from the Plugin Manager")
                + ")");
  }

  /**
   * Puts the bundled grammar on the editor the moment a TOML file is opened.
   *
   * <p>Waiting for the language server would be the obvious route and the wrong one: the server only
   * connects once the editor asks for it, and a config file should never sit there uncoloured in the
   * meantime. The provider declares the same scope, so both routes agree on what the file looks
   * like.
   */
  private void onFileEvent(FileEvent event) {
    if (event == null || event.type() != FileEvent.Type.OPENED) {
      return;
    }
    String path = event.path();
    if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(TOML_EXTENSION)) {
      return;
    }
    EditorHost editorHost = context.getServices().require(IdeHostServices.EDITOR_HOST);
    Object rawEditor = editorHost.getEditor();
    if (!(rawEditor instanceof CodeEditor editor)) {
      return;
    }
    TomlTextmateHost host = TomlTextmateHost.instance();
    if (host != null) {
      host.setWhenReady(editor, TomlTextmateHost.TOML_SCOPE);
    }
  }

}
