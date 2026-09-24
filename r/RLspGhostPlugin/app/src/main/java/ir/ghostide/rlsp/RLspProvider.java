package ir.ghostide.rlsp;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

public final class RLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList(
          "/usr/local/bin/r-languageserver",
          "/usr/bin/r-languageserver");

  private static final List<String> SERVER_ARGS = List.of();

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("r", "rmd", "rnw");

  private final ProotProcessLauncher launcher;
  private final RTextmateHost textmate;

  public RLspProvider(ProotProcessLauncher launcher, RTextmateHost textmate) {
    this.launcher = launcher;
    this.textmate = textmate;
  }

  @Override
  public String getId() {
    return "ir.ghostide.rlsp.lsp";
  }

  @Override
  public String getDisplayName() {
    return "R Language Server (languageserver)";
  }

  public boolean isInstalled() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    LspServerDefinition.Builder builder =
        LspServerDefinition.builder(getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
            .grammarScopeName("source.r")
            .textMateGrammarLink(
                "https://raw.githubusercontent.com/textmate/r.tmbundle/master/Syntaxes/R.plist")
            .enableInlayHints(true)
            .enableSignatureHelp(true)
            .initializationTimeoutMillis(120_000);
    if (textmate != null) {
      textmate.registerScope("source.r");
    }
    return builder.build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), findExecutable(), SERVER_ARGS);
  }

  private String findExecutable() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return path;
      }
    }
    return CANDIDATE_PATHS.get(0);
  }
}