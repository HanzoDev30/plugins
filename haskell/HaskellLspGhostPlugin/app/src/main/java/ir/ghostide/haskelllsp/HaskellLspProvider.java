package ir.ghostide.haskelllsp;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

public final class HaskellLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList(
          "/usr/local/bin/haskell-language-server",
          "/usr/bin/haskell-language-server",
          "/bin/haskell-language-server");

  private static final List<String> SERVER_ARGS = List.of("--lsp");

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("hs", "lhs");

  private final ProotProcessLauncher launcher;
  private final HaskellTextmateHost textmate;

  public HaskellLspProvider(ProotProcessLauncher launcher, HaskellTextmateHost textmate) {
    this.launcher = launcher;
    this.textmate = textmate;
  }

  @Override
  public String getId() {
    return "ir.ghostide.haskelllsp.lsp";
  }

  @Override
  public String getDisplayName() {
    return "Haskell Language Server (haskell-language-server)";
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
            .grammarScopeName("source.haskell")
            .textMateGrammarLink(
                "https://raw.githubusercontent.com/textmate/haskell.tmbundle/master/Syntaxes/Haskell.plist")
            .enableInlayHints(true)
            .enableSignatureHelp(true)
            .initializationTimeoutMillis(120_000);
    if (textmate != null) {
      textmate.registerScope("source.haskell");
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