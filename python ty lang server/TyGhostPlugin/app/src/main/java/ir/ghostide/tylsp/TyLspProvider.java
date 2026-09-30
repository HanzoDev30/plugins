package ir.ghostide.tylsp;

import java.util.Collections;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/**
 * Routes {@code .py} files to ty (astral-sh/ty) running inside the host's proot rootfs.
 */
public final class TyLspProvider implements LspServerProvider {

  private static final String GUEST_EXECUTABLE = "/usr/local/bin/ty-language-server";

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("py");

  private final ProotProcessLauncher launcher;

  public TyLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.tylsp";
  }

  @Override
  public String getDisplayName() {
    return "Ty (Astral) Language Server";
  }

  @Override
  public int getPriority() {
    return 100;
  }

  public boolean isInstalled() {
    return launcher.isInstalled(GUEST_EXECUTABLE);
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    return LspServerDefinition.builder(getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .grammarScopeName("source.python")
        .initializationTimeoutMillis(15_000)
        .enableInlayHints(true)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), GUEST_EXECUTABLE, Collections.emptyList());
  }
}
