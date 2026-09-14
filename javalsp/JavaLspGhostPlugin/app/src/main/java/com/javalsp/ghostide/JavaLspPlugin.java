package com.javalsp.ghostide;

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
 * Installs and runs Eclipse JDT Language Server (jdtls) inside the proot rootfs. Requires an
 * existing jdtls installation (e.g. {@code ~/jdtls}) and a Java 21+ runtime on the device; the
 * setup action locates jdtls automatically and creates a stable {@code
 * /usr/local/bin/java-language-server} wrapper that forwards to {@code jdtls --stdio}.
 *
 * <p>jdtls is the reference implementation of a Java language server, backed by Eclipse JDT: it
 * provides completions, hover, diagnostics, document/workspace symbols, formatting, code actions,
 * references, rename, type hierarchies and full project-awareness (Maven/Gradle classpath). The
 * installer shell script lives in {@code assets/install-java-lsp.sh} and is read at runtime through
 * the plugin's own Android Context.
 */
public final class JavaLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-java-lsp.sh";
  private static final String INSTALL_DIR = "/opt/java-lsp";

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
            "install-java-language-server",
            "Install Java language server (Eclipse JDT)",
            command,
            "Locates the existing Eclipse JDT Language Server (jdtls) installation "
                + "in the proot rootfs (searches ~/jdtls, /opt/jdtls and more), ensures "
                + "Java 21+ is available, and creates the /usr/local/bin/java-language-server "
                + "wrapper. Provides completions, hover, diagnostics, document symbols, "
                + "formatting, code actions, references, rename and more for .java files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    JavaLspProvider provider = new JavaLspProvider(launcher);
    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(registration);
    context
        .getLogger()
        .info(
            provider.isInstalled()
                ? "jdtls wrapper found in rootfs"
                : "jdtls not installed yet; run the setup action from the Plugin Manager");
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
