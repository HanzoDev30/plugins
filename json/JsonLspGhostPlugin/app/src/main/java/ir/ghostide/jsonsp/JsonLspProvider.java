package ir.ghostide.jsonsp;

import java.util.Collections;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/**
 * Routes JSON/JSONC files to vscode-json-language-server running inside the proot rootfs.
 */
public final class JsonLspProvider implements LspServerProvider {

  private static final String GUEST_EXECUTABLE = "/usr/local/bin/json-language-server";

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("json", "jsonc");

  private final ProotProcessLauncher launcher;

  public JsonLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.jsonsp";
  }

  @Override
  public String getDisplayName() {
    return "JSON Language Server (vscode-json-language-server)";
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
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), GUEST_EXECUTABLE, Collections.emptyList());
  }
}