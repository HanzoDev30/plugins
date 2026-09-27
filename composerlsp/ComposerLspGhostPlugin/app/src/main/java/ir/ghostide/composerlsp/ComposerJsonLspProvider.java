package ir.ghostide.composerlsp;

import java.util.Collections;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/**
 * Routes {@code composer.json} and {@code composer.lock} to vscode-json-language-server running
 * inside the proot rootfs.
 *
 * <p>That server ships the official Composer JSON schema, so opening a manifest gives schema-aware
 * completion for the {@code require} / {@code require-dev} / {@code scripts} keys, validation of the
 * manifest and diagnostics on a broken dependency constraint. Registered at a higher priority than
 * the plain JSON provider, but {@link #supports(LspServerRequest)} only answers for the two
 * Composer filenames, so every other {@code .json} file still goes to the JSON language server.
 */
public final class ComposerJsonLspProvider implements LspServerProvider {

  private static final String GUEST_EXECUTABLE = "/usr/local/bin/composer-json-language-server";

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("json");

  private static final Set<String> SUPPORTED_NAMES = Set.of("composer.json", "composer.lock");

  private final ProotProcessLauncher launcher;

  public ComposerJsonLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.composerlsp.json";
  }

  @Override
  public String getDisplayName() {
    return "Composer Manifest Language Server (vscode-json-language-server)";
  }

  @Override
  public String getDescription() {
    return "Schema-aware completion and validation for composer.json and composer.lock.";
  }

  public boolean isInstalled() {
    return launcher.isInstalled(GUEST_EXECUTABLE);
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    if (!"json".equals(request.extension())) {
      return false;
    }
    String name = request.file().getName().toLowerCase(java.util.Locale.ROOT);
    return SUPPORTED_NAMES.contains(name);
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .grammarScopeName("source.json")
        .initializationTimeoutMillis(15_000)
        .enableInlayHints(true)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), GUEST_EXECUTABLE, Collections.emptyList());
  }
}
