package ir.ghostide.rubylsp;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/** Routes Ruby (.rb) files to solargraph running inside the proot rootfs. */
public final class RubyLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      List.of("/usr/local/bin/solargraph", "/usr/bin/solargraph");

  private static final List<String> SERVER_ARGS = List.of("stdio");

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("rb");

  private final ProotProcessLauncher launcher;

  public RubyLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.rubylsp";
  }

  @Override
  public String getDisplayName() {
    return "Ruby Language Server (solargraph)";
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