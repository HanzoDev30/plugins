package ir.ghostide.tslsp;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

public final class TsLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList("/usr/bin/tsc", "/usr/local/bin/tsc");

  private static final List<String> SERVER_ARGS = Arrays.asList("--lsp", "-stdio");

  private static final Set<String> SUPPORTED_EXTENSIONS =
      Set.of("js", "mjs", "cjs", "jsx", "ts", "tsx");

  private final ProotProcessLauncher launcher;

  public TsLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.tslsp";
  }

  @Override
  public String getDisplayName() {
    return "TypeScript Language Server (tsc --lsp)";
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
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .build();
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