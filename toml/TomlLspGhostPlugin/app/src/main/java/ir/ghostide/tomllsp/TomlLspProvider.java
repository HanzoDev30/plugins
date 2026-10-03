package ir.ghostide.tomllsp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Routes TOML files to the Taplo language server inside the proot rootfs.
 *
 * <p>The setup action fetches the official static release, which has the LSP compiled in - the npm
 * package of the same name is a formatter-only build and refuses to serve ({@code the LSP is not
 * part of this build}). The grammar is bundled with the plugin, so the link below is only a hint
 * for a host that prefers to fetch one itself.
 */
public final class TomlLspProvider implements LspServerProvider {

  private static final String GRAMMAR_LINK =
      "https://raw.githubusercontent.com/textmate/toml.tmbundle/master/Syntaxes/TOML.tmLanguage";

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList("/usr/local/bin/taplo", "/usr/bin/taplo", "/opt/toml-lsp/taplo");

  /**
   * {@code lsp} on its own only prints usage: the server is a subcommand of it and the transport is
   * chosen there too. Without the trailing {@code stdio} taplo starts, hands back its help text and
   * exits, which the host reports as a missing capabilities response.
   */
  private static final List<String> SERVER_ARGS = List.of("lsp", "stdio");

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("toml");

  private static final int INITIALIZATION_TIMEOUT_MILLIS = 60_000;

  private final ProotProcessLauncher launcher;
  private final TomlTextmateHost textmate;
  private final PluginLogger logger;

  public TomlLspProvider(
      ProotProcessLauncher launcher, TomlTextmateHost textmate, PluginLogger logger) {
    this.launcher = launcher;
    this.textmate = textmate;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.ghostide.tomllsp";
  }

  @Override
  public String getDisplayName() {
    return "Taplo TOML Language Server";
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
    logger.info("toml: lsp definition for " + request.file().getName());
    if (textmate != null) {
      textmate.registerScope(TomlTextmateHost.TOML_SCOPE);
    }
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .grammarScopeName(TomlTextmateHost.TOML_SCOPE)
        .textMateGrammarLink(GRAMMAR_LINK)
        .enableInlayHints(true)
        .enableSignatureHelp(true)
        .initializationTimeoutMillis(INITIALIZATION_TIMEOUT_MILLIS)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    String projectRoot = request.projectRoot().getAbsolutePath();
    String executable = findExecutable();
    if (executable != null) {
      logger.info(
          "toml: launching \"" + executable + "\""
              + " args="
              + String.join(" ", SERVER_ARGS)
              + " cwd="
              + projectRoot);
      return launcher.launch(projectRoot, executable, SERVER_ARGS);
    }
    logger.error("toml: no usable TOML language server (checked " + String.join(", ", CANDIDATE_PATHS) + ")", null);
    throw new IllegalStateException(
        "TOML language server is not installed, run the plugin setup action first");
  }

  private String findExecutable() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return path;
      }
    }
    return null;
  }
}