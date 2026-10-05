package ir.hanzodev1375.svelte;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/** One language server, started through a wrapper script the setup action installs. */
final class SvelteLspProvider implements LspServerProvider {

  private final String id;
  private final String displayName;
  private final int priority;
  private final Set<String> extensions;
  private final Predicate<File> matcher;
  private final String grammarScope;
  private final String wrapper;
  private final List<String> args;
  private final ProotProcessLauncher launcher;
  private final PluginLogger logger;

  SvelteLspProvider(
      String id,
      String displayName,
      int priority,
      Set<String> extensions,
      Predicate<File> matcher,
      String grammarScope,
      String wrapper,
      List<String> args,
      ProotProcessLauncher launcher,
      PluginLogger logger) {
    this.id = id;
    this.displayName = displayName;
    this.priority = priority;
    this.extensions = extensions;
    this.matcher = matcher;
    this.grammarScope = grammarScope;
    this.wrapper = wrapper;
    this.args = args;
    this.launcher = launcher;
    this.logger = logger;
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
    return priority;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return request != null && request.file() != null && matcher.test(request.file());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("svelte: lsp definition for " + request.file().getName());
    LspServerDefinition.Builder builder =
        LspServerDefinition.builder(id, extensions, displayName, this::connect)
            .enableSignatureHelp(true)
            .enableInlayHints(false)
            .initializationTimeoutMillis(120_000);
    if (grammarScope != null) {
      builder.grammarScopeName(grammarScope);
    }
    return builder.build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    String cwd = request.projectRoot().getAbsolutePath();
    if (launcher.isInstalled(wrapper)) {
      logger.info("svelte: launching " + wrapper + " " + args + " in " + cwd);
      return launcher.launch(cwd, wrapper, args);
    }
    logger.error("svelte: " + wrapper + " is not installed", null);
    throw new IllegalStateException(
        "Svelte language server is not installed, run the plugin setup action first");
  }
}
