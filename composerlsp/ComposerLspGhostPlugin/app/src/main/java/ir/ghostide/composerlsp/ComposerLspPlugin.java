package ir.ghostide.composerlsp;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ir.ghostide.composerlsp.checker.ComposerCheckDependenciesCommand;
import ir.ghostide.composerlsp.checker.ComposerCheckerService;
import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.CoreExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * Composer Lsp — schema-aware language support for {@code composer.json} and {@code composer.lock}.
 *
 * <p>Registers a language server provider that only answers for the two Composer filenames and runs
 * vscode-json-language-server inside the proot rootfs, at a priority above the plain JSON provider.
 * The manifest therefore gets completion for the {@code require} / {@code scripts} keys, validation
 * and diagnostics, while every other {@code .json} file still goes to the JSON language server.
 *
 * <p>On top of that it contributes a Packagist dependency checker for {@code composer.json}: the
 * version token of a {@code require} / {@code require-dev} entry is highlighted in the same colour
 * the IDE uses for outdated Gradle coordinates whenever Packagist has a release the declared
 * constraint does not allow, and tapping it offers to rewrite the constraint in place.
 *
 * <p>Nothing needs a terminal by hand: when the server is missing, the install script is pushed to
 * the IDE terminal automatically.
 */
public final class ComposerLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-composer-lsp.sh";
  private static final String INSTALL_DIR = "/opt/composer-lsp";

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
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\n"
            + "chmod +x "
            + INSTALL_DIR
            + "/install.sh\n"
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-composer-lsp",
            "Install PHP, Composer and the manifest language server",
            command,
            "Installs the PHP CLI plus its common extensions, the Composer dependency manager and "
                + "vscode-json-language-server inside the proot rootfs. Creates "
                + "/usr/local/bin/composer and /usr/local/bin/composer-json-language-server so the "
                + "manifest, the terminal and composer itself all work."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    ComposerJsonLspProvider provider = new ComposerJsonLspProvider(launcher);
    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                100);
    context.registerDisposable(registration);

    registerDependencyChecker(context);

    if (provider.isInstalled()) {
      context.getLogger().info("composer-json-language-server wrapper found in rootfs");
      return;
    }
    context.getLogger().info("manifest language server missing - sending the install to the terminal");
    ComposerLspInstaller.installNow(context, installCommand());
  }

  /** Wires the Packagist dependency checker to the editor and to the command palette. */
  private void registerDependencyChecker(PluginContext context) {
    ComposerCheckerService service = new ComposerCheckerService(context);
    context.registerDisposable(service);

    String owner = context.getDescriptor().getId();
    context.registerDisposable(
        context
            .getExtensions()
            .register(IdeEvents.FILE_EVENT, service::onFileEvent, owner, 0));
    context.registerDisposable(
        context
            .getExtensions()
            .register(
                CoreExtensionPoints.PLUGIN_COMMAND,
                new ComposerCheckDependenciesCommand(service),
                owner,
                0));
  }

  private String installCommand() {
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (body.isBlank()) {
      return "";
    }
    return "mkdir -p "
        + INSTALL_DIR
        + "\n"
        + "cat > "
        + INSTALL_DIR
        + "/install.sh <<'INSTALL_EOF'\n"
        + body
        + "\nINSTALL_EOF\n"
        + "chmod +x "
        + INSTALL_DIR
        + "/install.sh\n"
        + INSTALL_DIR
        + "/install.sh\n";
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
