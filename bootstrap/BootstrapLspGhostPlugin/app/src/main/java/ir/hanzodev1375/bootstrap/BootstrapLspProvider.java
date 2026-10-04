package ir.hanzodev1375.bootstrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Bootstrap class completion for HTML. A file only gets one language server, so this runs the
 * stock html and Emmet servers behind a proxy (assets/bootstrap-lsp.js) instead of replacing them.
 */
public final class BootstrapLspProvider implements LspServerProvider {

  static final int PRIORITY = 500;

  private static final String INSTALL_DIR = "/opt/bootstrap-lsp";
  private static final String WRAPPER = "/usr/local/bin/bootstrap-language-server";
  private static final String NODE = "/usr/bin/node";
  private static final String RAW_SERVER = INSTALL_DIR + "/server.js";
  private static final String CONFIG = INSTALL_DIR + "/config.json";

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("html", "htm");
  private static final List<String> ANGULAR_MARKERS = List.of("angular.json", ".angular.json");
  private static final int MAX_PARENT_LEVELS = 8;
  private static final int SNIFF_BYTES = 128 * 1024;

  private final ProotProcessLauncher launcher;
  private final PluginLogger logger;

  public BootstrapLspProvider(ProotProcessLauncher launcher, PluginLogger logger) {
    this.launcher = launcher;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.bootstrap.lsp";
  }

  @Override
  public String getDisplayName() {
    return "Bootstrap + Emmet + HTML (proxy)";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    if (request == null || request.file() == null) {
      return false;
    }
    if (!SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase(Locale.ROOT))) {
      return false;
    }
    // Priority 500 outranks the Angular plugin (400); leave Angular workspaces to it.
    if (inAngularWorkspace(request.file())) {
      return false;
    }
    return usesBootstrap(request);
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("bootstrap: lsp definition for " + request.file().getName());
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .initializationOptions(
            Map.of(
                "embeddedLanguages", Map.of("css", true, "javascript", true),
                "provideFormatter", true))
        .enableSignatureHelp(false)
        .enableInlayHints(false)
        .initializationTimeoutMillis(60_000)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    String cwd = request.projectRoot().getAbsolutePath();
    if (launcher.isInstalled(WRAPPER)) {
      if (!probe(CONFIG)) {
        logger.warn("bootstrap: " + CONFIG + " is missing, run the plugin setup action again (needed for Emmet)");
      }
      logger.info("bootstrap: launching " + WRAPPER + " in " + cwd);
      return launcher.launch(cwd, WRAPPER, List.of("--stdio"));
    }
    if (launcher.isInstalled(NODE) && launcher.isInstalled(RAW_SERVER) && launcher.isInstalled(CONFIG)) {
      // server.js finds the html and emmet servers through config.json
      List<String> args = new ArrayList<>();
      args.add(RAW_SERVER);
      args.add("--stdio");
      logger.info("bootstrap: launching " + NODE + " " + args + " in " + cwd);
      return launcher.launch(cwd, NODE, args);
    }
    logger.error(
        "bootstrap: no usable server (wrapper=" + probe(WRAPPER) + ", node=" + probe(NODE)
            + ", server.js=" + probe(RAW_SERVER) + ", config=" + probe(CONFIG) + ")",
        null);
    throw new IllegalStateException(
        "Bootstrap language server is not installed, run the plugin setup action first");
  }

  private static boolean inAngularWorkspace(File file) {
    File dir = file.getParentFile();
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++) {
      for (String marker : ANGULAR_MARKERS) {
        if (new File(dir, marker).isFile()) {
          return true;
        }
      }
      dir = dir.getParentFile();
    }
    return false;
  }

  /** node_modules/bootstrap, a "bootstrap" dependency, or the file itself mentioning bootstrap. */
  private static boolean usesBootstrap(LspServerRequest request) {
    File dir = request.file().getParentFile();
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++) {
      if (new File(dir, "node_modules/bootstrap").isDirectory()) {
        return true;
      }
      File pkg = new File(dir, "package.json");
      if (pkg.isFile() && read(pkg, 64 * 1024).contains("\"bootstrap\"")) {
        return true;
      }
      dir = dir.getParentFile();
    }
    return read(request.file(), SNIFF_BYTES).toLowerCase(Locale.ROOT).contains("bootstrap");
  }

  private static String read(File f, int limit) {
    try (InputStream in = new FileInputStream(f)) {
      byte[] buf = new byte[limit];
      int total = 0;
      int n;
      while (total < limit && (n = in.read(buf, total, limit - total)) > 0) {
        total += n;
      }
      return new String(buf, 0, total, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }

  private boolean probe(String path) {
    try {
      return launcher.isInstalled(path);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
