package ir.ghostide.kmplsp;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * Installs and runs kmp-lsp inside the proot rootfs.
 *
 * <p>The setup action writes {@code assets/install-kmp-lsp.sh} to {@code /opt/kmp-lsp/install.sh}
 * with a single heredoc and executes it, so the installer on disk is exactly the readable script
 * shipped in the APK - the same thing a user gets by pasting it into a terminal. The script
 * downloads the release tarball, verifies it against the published {@code sha256sums.txt}, unpacks
 * both binaries into {@code /opt/kmp-lsp} and writes the {@code /usr/local/bin/kmp-lsp} wrapper
 * this plugin launches.
 */
public final class KmpLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-kmp-lsp.sh";

  /** The name the installer is known by inside the rootfs; the plugin manager runs it directly. */
  private static final String INSTALL_SCRIPT = "install.sh";

  private static final String INSTALL_DIR = "/opt/kmp-lsp";

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
            + " <<'KMP_ASSET_EOF'\n"
            + body
            + "\nKMP_ASSET_EOF\n"
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
            "install-kmp-lsp",
            "Install KMP LSP (kmp-lsp)",
            command,
            "Installs kmp-lsp, a Rust/tree-sitter language server for Kotlin, Java and Swift, plus "
                + "its native jar-indexer sidecar, into the rootfs. Also installs ripgrep and "
                + "fd-find (with the fd -> fdfind symlink kmp-lsp expects) so cross-file search and "
                + "file discovery work. Re-running the action on the same version only refreshes the "
                + "launcher."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    String pluginId = context.getDescriptor().getId();
    // One provider per language. The host resolves a provider per file extension, so a provider
    // claiming several extensions only ever gets one of them a server - the rest end up with no
    // connection at all, and the host ends up pairing the wrong grammar with the file.
    List<KmpLspProvider> providers =
        List.of(
            new KotlinKmpLspProvider(launcher),
            new GradleKtsKmpLspProvider(launcher),
            new JavaKmpLspProvider(launcher),
            new SwiftKmpLspProvider(launcher));
    for (KmpLspProvider provider : providers) {
      Disposable registration =
          context
              .getExtensions()
              .register(
                  EditorExtensionPoints.LSP_SERVER_PROVIDER,
                  provider,
                  pluginId,
                  KmpLspProvider.PRIORITY);
      context.registerDisposable(registration);
    }
    context
        .getLogger()
        .info(
            providers.get(0).isInstalled()
                ? "kmp-lsp found in rootfs"
                : "kmp-lsp not installed yet; run the setup action from the Plugin Manager");
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
