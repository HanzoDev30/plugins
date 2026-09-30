package ir.hanzodev1375.astro;

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

public final class AstroLspProvider implements LspServerProvider {

  private static final String WRAPPER = "/usr/local/bin/astro-language-server";
  private static final String NODE = "/usr/bin/node";
  private static final String SERVER_ENTRY =
      "/usr/lib/node_modules/@astrojs/language-server/bin/nodeServer.js";

  private static final List<String> CANDIDATE_PATHS =
      List.of(WRAPPER, "/usr/bin/astro-ls", "/usr/local/bin/astro-ls");

  private static final List<String> SERVER_ARGS = List.of("--stdio");
  private static final List<String> RAW_SERVER_ARGS = List.of(SERVER_ENTRY, "--stdio");

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("astro", "mdx");

  private static final String GRAMMAR_SCOPE = AstroTextmateHost.ASTRO_SCOPE;

  private static final String GRAMMAR_LINK =
      "https://raw.githubusercontent.com/withastro/astro/main/packages/language-tools/vscode/syntaxes/astro.tmLanguage.json";

  private static final String SETUP_TSDK_DIR = "/opt/astro-lsp/node_modules/typescript/lib";

  private final ProotProcessLauncher launcher;
  private final AstroTextmateHost textmate;
  private final PluginLogger logger;

  public AstroLspProvider(
      ProotProcessLauncher launcher, AstroTextmateHost textmate, PluginLogger logger) {
    this.launcher = launcher;
    this.textmate = textmate;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.astro.lsp";
  }

  @Override
  public String getDisplayName() {
    return "Astro Language Server (@astrojs/language-server)";
  }

  @Override
  public int getPriority() {
    return 350;
  }

  public boolean isInstalled() {
    return findExecutable() != null;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return request != null
        && SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase(Locale.ROOT));
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    String tsdk = resolveTsdk(request.projectRoot());
    logger.info("astro: lsp definition for " + request.file().getName() + ", tsdk=" + tsdk);
    LspServerDefinition.Builder builder =
        LspServerDefinition.builder(getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
            .grammarScopeName(GRAMMAR_SCOPE)
            .textMateGrammarLink(GRAMMAR_LINK)
            .initializationOptions(Map.of("typescript", Map.of("tsdk", tsdk)))
            .enableInlayHints(true)
            .enableSignatureHelp(true)
            .initializationTimeoutMillis(120_000);
    if (textmate != null) {
      textmate.registerScope(GRAMMAR_SCOPE);
    }
    return builder.build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    String projectRoot = request.projectRoot().getAbsolutePath();
    String executable = findExecutable();
    if (executable != null) {
      logger.info("astro: launching " + executable + " " + SERVER_ARGS + " in " + projectRoot);
      return launcher.launch(projectRoot, executable, SERVER_ARGS);
    }
    if (launcher.isInstalled(NODE) && launcher.isInstalled(SERVER_ENTRY)) {
      logger.info("astro: launching " + NODE + " " + RAW_SERVER_ARGS + " in " + projectRoot);
      return launcher.launch(projectRoot, NODE, RAW_SERVER_ARGS);
    }
    logger.error(
        "astro: no usable Astro language server (wrapper=" + probe(WRAPPER) + ", node=" + probe(NODE)
            + ", entry=" + probe(SERVER_ENTRY) + ", bin=" + probe("/usr/bin/astro-ls") + ")",
        null);
    throw new IllegalStateException(
        "Astro language server is not installed, run the plugin setup action first");
  }

  private String findExecutable() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return path;
      }
    }
    return null;
  }

  private String resolveTsdk(File projectRoot) {
    List<String> candidates = new ArrayList<>();
    if (projectRoot != null) {
      candidates.add(projectRoot.getAbsolutePath() + "/node_modules/typescript/lib");
    }
    candidates.add(SETUP_TSDK_DIR);
    candidates.add("/usr/lib/node_modules/typescript/lib");
    candidates.add("/usr/local/lib/node_modules/typescript/lib");
    for (String dir : candidates) {
      if (launcher.isInstalled(dir + "/typescript.js")) {
        return dir;
      }
    }
    return SETUP_TSDK_DIR;
  }

  private boolean probe(String path) {
    try {
      return launcher.isInstalled(path);
    } catch (RuntimeException e) {
      return false;
    }
  }
}