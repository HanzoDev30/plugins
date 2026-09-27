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
 * shipped in the APK — the same thing a user gets by pasting it into a terminal. The script
 * downloads the release binaries, verifies them against the published {@code sha256sums.txt},
 * installs them into {@code /opt/kmp-lsp} and writes the {@code /usr/local/bin/kmp-lsp} wrapper
 * this plugin launches.
 *
 * <p>Two more assets go next to it, because the server can only see what it is pointed at:
 * {@code kmp-env.sh} resolves the Android SDK, the Gradle cache and the JDK (kmp-lsp finds
 * android.jar and {@code *-sources.jar} only through those), and {@code prepare.sh} writes the
 * project's {@code workspace.json} and reports what will be indexed. They are written as files of
 * their own rather than inlined into the installer, so the launcher and the preparation can never
 * disagree about where the SDK is.
 */
public final class KmpLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-kmp-lsp.sh";

  /** Written next to the installer and sourced by the launcher; see the class comment. */
  private static final String ENV_SCRIPT_ASSET = "kmp-env.sh";

  private static final String PREPARE_SCRIPT_ASSET = "prepare-workspace.sh";

  /**
   * The names the scripts are known by inside the rootfs. The launcher, the installer and the
   * plugin manager all address them by these, so they are spelled out here rather than derived from
   * the asset name: an asset called {@code prepare-workspace.sh} is {@code prepare.sh} on disk.
   */
  private static final String INSTALL_SCRIPT = "install.sh";

  private static final String ENV_SCRIPT = "kmp-env.sh";
  private static final String PREPARE_SCRIPT = "prepare.sh";

  private static final String INSTALL_DIR = "/opt/kmp-lsp";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    if (readAsset(context, INSTALL_SCRIPT_ASSET).isBlank()) {
      return List.of();
    }
    StringBuilder command = new StringBuilder("mkdir -p ").append(INSTALL_DIR).append('\n');
    // Written before the installer runs: its own main() checks for them, and the launcher it writes
    // sources one of them on every single start.
    if (!writeAsset(command, ENV_SCRIPT_ASSET, ENV_SCRIPT)
        || !writeAsset(command, PREPARE_SCRIPT_ASSET, PREPARE_SCRIPT)
        || !writeAsset(command, INSTALL_SCRIPT_ASSET, INSTALL_SCRIPT)) {
      return List.of();
    }
    command.append(INSTALL_DIR).append('/').append(INSTALL_SCRIPT).append('\n');
    return List.of(
        new PluginSetupAction(
            "install-kmp-lsp",
            "Install KMP LSP (kmp-lsp)",
            command.toString(),
            "Installs kmp-lsp, a Rust/tree-sitter language server for Kotlin, Java and Swift, plus "
                + "its native jar-indexer sidecar, into the rootfs. Also installs ripgrep and "
                + "fd-find (with the fd -> fdfind symlink kmp-lsp expects) so cross-file search and "
                + "file discovery work. Re-running the action on the same version only refreshes "
                + "the launcher, and points the server at the Android SDK and the Gradle cache that "
                + "the AndroidBuilder plugin installs into."));
  }

  /** One asset, one heredoc, one chmod. The delimiter cannot occur in a shell script. */
  private boolean writeAsset(StringBuilder command, String asset, String name) {
    String body = readAsset(context, asset);
    if (body.isBlank()) {
      context.getLogger().error("Missing asset: " + asset);
      return false;
    }
    command
        .append("cat > ")
        .append(INSTALL_DIR)
        .append('/')
        .append(name)
        .append(" <<'KMP_ASSET_EOF'\n")
        .append(body)
        .append("\nKMP_ASSET_EOF\n")
        .append("chmod +x ")
        .append(INSTALL_DIR)
        .append('/')
        .append(name)
        .append('\n');
    return true;
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
