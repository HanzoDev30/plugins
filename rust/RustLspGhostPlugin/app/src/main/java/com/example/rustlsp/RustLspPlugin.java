package com.example.rustlsp;

import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorActionHandler;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * GhostIDE plugin that adds Rust language support via rust-analyzer.
 *
 * <p>Setup actions install the Rust toolchain and rust-analyzer inside the host's proot rootfs.
 * After installation the plugin registers an {@link
 * ir.hanzodev1375.ghostide.ide.api.LspServerProvider} so .rs files get LSP features (completion,
 * diagnostics, hover, etc.).
 *
 * @author Ghost
 */
public final class RustLspPlugin implements GhostPlugin {

  private static final String INSTALL_TOOLCHAIN =
      "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y";

  private static final String INSTALL_ANALYZER =
      "source ~/.cargo/env && rustup component add rust-analyzer";

  private static final String VERIFY = "source ~/.cargo/env && rust-analyzer --version";

  private PluginContext pluginContext;
  private RustRunner runner;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    return List.of(
        new PluginSetupAction(
            "install-rust-toolchain",
            "Install Rust toolchain (rustup + cargo + rustc)",
            INSTALL_TOOLCHAIN,
            "Downloads and installs the Rust toolchain inside the sandboxed Linux environment. "
                + "Required before rust-analyzer can be installed."),
        new PluginSetupAction(
            "install-rust-analyzer",
            "Install rust-analyzer",
            INSTALL_ANALYZER,
            "Installs the rust-analyzer language server component via rustup. "
                + "Requires the Rust toolchain to be installed first."),
        new PluginSetupAction(
            "verify-rust-analyzer",
            "Verify rust-analyzer installation",
            VERIFY,
            "Runs rust-analyzer --version to confirm the language server is correctly installed."));
  }

  @Override
  public void activate(PluginContext context) {
    this.pluginContext = context;

    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);

    RustLspProvider lspProvider = new RustLspProvider(launcher);
    Disposable lspRegistration =
        context.getExtensions().register(EditorExtensionPoints.LSP_SERVER_PROVIDER, lspProvider);
    context.registerDisposable(lspRegistration);

    this.runner = new RustRunner(context);

    EditorActionHandler runFileHandler = createRunFileHandler();
    Disposable handlerRegistration =
        context
            .getExtensions()
            .register(PluginUiExtensionPoints.EDITOR_ACTION_HANDLER, runFileHandler);
    context.registerDisposable(handlerRegistration);

    context
        .getLogger()
        .info(
            lspProvider.isInstalled()
                ? "rust-analyzer found in rootfs"
                : "rust-analyzer not installed yet; run the setup actions from the Plugin Manager");
  }

  private EditorActionHandler createRunFileHandler() {
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

  public RustRunner getRunner() {
    return runner;
  }

  public PluginContext getPluginContext() {
    return pluginContext;
  }
}
