package ir.hanzodev1375.react;

import java.util.LinkedHashMap;
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
 * React language support backed by the TypeScript language server. JSX files (.tsx/.jsx) are always
 * claimed; plain .ts/.js files only when they belong to a React project, so the built-in TypeScript
 * server keeps handling everything else.
 */
public final class ReactLspProvider implements LspServerProvider {

  static final int PRIORITY = 300;

  private static final Set<String> JSX_EXTENSIONS = Set.of("tsx", "jsx");

  private static final Set<String> SUPPORTED_EXTENSIONS =
      Set.of("ts", "tsx", "js", "jsx", "mts", "cts", "mjs", "cjs");

  private static final List<String> WRAPPERS =
      List.of(
          "/usr/local/bin/react-language-server",
          "/usr/local/bin/typescript-language-server",
          "/usr/bin/typescript-language-server");

  private static final String NODE = "/usr/bin/node";

  private static final String GLOBAL_ENTRY =
      "/usr/lib/node_modules/typescript-language-server/lib/cli.mjs";

  private static final String TSC = "/usr/bin/tsc";

  private static final List<String> STDIO_ARGS = List.of("--stdio");

  private static final List<String> TSC_ARGS = List.of("--lsp", "-stdio");

  private final ProotProcessLauncher launcher;
  private final PluginLogger logger;

  public ReactLspProvider(ProotProcessLauncher launcher, PluginLogger logger) {
    this.launcher = launcher;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.react.lsp";
  }

  @Override
  public String getDisplayName() {
    return "React Language Server (TypeScript)";
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
    String extension = request.extension().toLowerCase(Locale.ROOT);
    if (JSX_EXTENSIONS.contains(extension)) {
      return true;
    }
    if (!SUPPORTED_EXTENSIONS.contains(extension)) {
      return false;
    }
    try {
      return ReactProject.findRoot(request.file(), request.projectRoot()) != null;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("react: lsp definition for " + request.file().getName());
    LspServerDefinition.Builder builder =
        LspServerDefinition.builder(getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
            .initializationOptions(initializationOptions())
            .enableInlayHints(true)
            .enableSignatureHelp(true)
            .initializationTimeoutMillis(120_000);
    String scope = ReactTextmateHost.scopeForExtension(request.extension());
    if (scope != null) {
      builder.grammarScopeName(scope);
    }
    return builder.build();
  }

  private static Map<String, Object> initializationOptions() {
    Map<String, Object> preferences = new LinkedHashMap<>();
    preferences.put("includeInlayParameterNameHints", "literals");
    preferences.put("includeInlayParameterNameHintsWhenArgumentMatchesName", false);
    preferences.put("includeInlayFunctionParameterTypeHints", true);
    preferences.put("includeInlayVariableTypeHints", true);
    preferences.put("includeInlayPropertyDeclarationTypeHints", true);
    preferences.put("includeInlayFunctionLikeReturnTypeHints", true);
    preferences.put("includeInlayEnumMemberValueHints", true);

    Map<String, Object> options = new LinkedHashMap<>();
    options.put("hostInfo", "GhostIDE");
    options.put("preferences", preferences);
    return options;
  }

  private LspServerConnection connect(LspServerRequest request) {
    String cwd = request.projectRoot().getAbsolutePath();
    for (String wrapper : WRAPPERS) {
      if (isInstalled(wrapper)) {
        logger.info("react: launching " + wrapper + " " + STDIO_ARGS + " in " + cwd);
        return launcher.launch(cwd, wrapper, STDIO_ARGS);
      }
    }
    if (isInstalled(NODE) && isInstalled(GLOBAL_ENTRY)) {
      logger.info("react: launching " + NODE + " " + GLOBAL_ENTRY);
      return launcher.launch(cwd, NODE, List.of(GLOBAL_ENTRY, "--stdio"));
    }
    if (isInstalled(TSC)) {
      logger.info("react: launching " + TSC + " " + TSC_ARGS);
      return launcher.launch(cwd, TSC, TSC_ARGS);
    }
    throw new IllegalStateException(
        "TypeScript/React language server is not installed; run the plugin setup action first");
  }

  private boolean isInstalled(String path) {
    try {
      return launcher.isInstalled(path);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
