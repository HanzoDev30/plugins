package ir.ghostide.glsllsp;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Routes one shader extension to glsl_analyzer.
 *
 * <p>One instance per extension and each definition covers exactly that one extension. The host
 * resolves a provider per extension and wraps the definition it gets in a definition of its own
 * carrying that extension, so a single definition claiming several extensions does not map cleanly
 * onto that: only one of them would ever get a server and the rest end up with no connection at
 * all. Keeping the mapping 1:1 also keeps the host's grammar pairing straight, which is what stops a
 * {@code .frag} file from being treated with the vertex grammar.
 *
 * <p>glsl_analyzer is a single static Rust binary with no runtime dependencies, so it starts
 * instantly and needs no toolchain, no SDK and no project import. It infers the shader stage from
 * the file extension, which is exactly why the extension mapping above is not a detail: a `.tesc`
 * file has to reach the server as a `.tesc` file.
 *
 * <p>glsl_analyzer talks JSON-RPC over stdio by default, so no arguments are forwarded.
 */
public final class GlslLspProvider implements LspServerProvider {

  private static final String GUEST_EXECUTABLE = "/usr/local/bin/glsl_analyzer";

  /**
   * Priority these providers are registered with, and the value {@link #getPriority()} reports.
   *
   * <p>Shader files are frequently included from project code, and the host already ships servers
   * that claim some of these extensions indirectly. Sitting above the defaults guarantees the
   * shader server is the one that answers instead of a general-purpose one that knows nothing about
   * GLSL builtins.
   */
  public static final int PRIORITY = 200;

  /**
   * The binary is a 5 MB static musl executable, so the handshake is fast; the generous timeout is
   * for proot, whose first exec of a freshly paged-in binary can outlast a short client-side
   * connect timeout even though nothing is wrong.
   */
  private static final int INITIALIZATION_TIMEOUT_MILLIS = 30_000;

  /**
   * The grammar is bundled with the plugin, so this link is only a hint for a host that prefers to
   * fetch one itself. The asset copy is what actually gets loaded.
   */
  private static final String GRAMMAR_LINK =
      "https://raw.githubusercontent.com/kjj6198/glsl-textmate/master/glsl.tmLanguage.json";

  private final ProotProcessLauncher launcher;
  private final GlslTextmateHost textmate;
  private final PluginLogger logger;
  private final String extension;
  private final String id;
  private final String displayName;

  GlslLspProvider(
      ProotProcessLauncher launcher,
      GlslTextmateHost textmate,
      PluginLogger logger,
      String extension,
      String stage) {
    this.launcher = launcher;
    this.textmate = textmate;
    this.logger = logger;
    this.extension = extension;
    this.id = "ir.ghostide.glsllsp." + extension;
    this.displayName = "glsl_analyzer (" + stage + ")";
  }

  /** True once the plugin's setup action has installed the launcher into the rootfs. */
  public boolean isInstalled() {
    return launcher.isInstalled(GUEST_EXECUTABLE);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return extension.equals(request.extension().toLowerCase(Locale.ROOT));
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("glsl: lsp definition for " + request.file().getName());
    if (textmate != null) {
      textmate.registerScope(GlslTextmateHost.GLSL_SCOPE);
    }
    return LspServerDefinition.builder(id, Set.of(extension), displayName, this::connect)
        .grammarScopeName(GlslTextmateHost.GLSL_SCOPE)
        .textMateGrammarLink(GRAMMAR_LINK)
        .enableInlayHints(true)
        .enableSignatureHelp(true)
        .initializationTimeoutMillis(INITIALIZATION_TIMEOUT_MILLIS)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), GUEST_EXECUTABLE, Collections.emptyList());
  }
}
