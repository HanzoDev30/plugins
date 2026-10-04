package ir.hanzodev1375.csspeek;

import java.io.File;
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
 * CSS Peek for HTML. A file only gets one language server, so this runs the stock html and Emmet
 * servers behind a proxy (assets/css-peek-lsp.js) that adds go-to-definition / hover for classes
 * and ids, plus Bootstrap class completion in Bootstrap projects.
 */
public final class CssPeekLspProvider implements LspServerProvider {

  /** Above the Bootstrap plugin (500) and the Angular plugin (400): this server includes both jobs. */
  static final int PRIORITY = 600;

  private static final String INSTALL_DIR = "/opt/csspeek-lsp";
  private static final String WRAPPER = "/usr/local/bin/css-peek-language-server";
  private static final String NODE = "/usr/bin/node";
  private static final String RAW_SERVER = INSTALL_DIR + "/server.js";
  private static final String CONFIG = INSTALL_DIR + "/config.json";

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("html", "htm");
  private static final List<String> ANGULAR_MARKERS = List.of("angular.json", ".angular.json");
  private static final int MAX_PARENT_LEVELS = 8;

  private final ProotProcessLauncher launcher;
  private final PluginLogger logger;

  public CssPeekLspProvider(ProotProcessLauncher launcher, PluginLogger logger) {
    this.launcher = launcher;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.csspeek.lsp";
  }

  @Override
  public String getDisplayName() {
    return "CSS Peek + HTML + Emmet + Bootstrap (proxy)";
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
    // Priority 600 outranks the Angular plugin (400); leave Angular workspaces to it.
    return !inAngularWorkspace(request.file());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("csspeek: lsp definition for " + request.file().getName());
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
      logger.info("csspeek: launching " + WRAPPER + " in " + cwd);
      return launcher.launch(cwd, WRAPPER, List.of("--stdio"));
    }
    if (launcher.isInstalled(NODE) && launcher.isInstalled(RAW_SERVER) && launcher.isInstalled(CONFIG)) {
      // server.js finds the html and emmet servers through config.json
      List<String> args = new ArrayList<>();
      args.add(RAW_SERVER);
      args.add("--stdio");
      logger.info("csspeek: launching " + NODE + " " + args + " in " + cwd);
      return launcher.launch(cwd, NODE, args);
    }
    logger.error(
        "csspeek: no usable server (wrapper=" + probe(WRAPPER) + ", node=" + probe(NODE)
            + ", server.js=" + probe(RAW_SERVER) + ", config=" + probe(CONFIG) + ")",
        null);
    throw new IllegalStateException(
        "CSS Peek language server is not installed, run the plugin setup action first");
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

  private boolean probe(String path) {
    try {
      return launcher.isInstalled(path);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
