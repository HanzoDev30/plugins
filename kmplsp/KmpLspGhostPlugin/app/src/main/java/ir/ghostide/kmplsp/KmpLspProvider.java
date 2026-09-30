package ir.ghostide.kmplsp;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/**
 * Base for the per-language kmp-lsp providers.
 *
 * <p>One subclass per file extension, and each definition covers exactly that one extension. The
 * host resolves a provider per extension and wraps the definition it gets in a definition of its
 * own carrying that extension, so a single definition claiming several extensions does not map
 * cleanly onto that: only one of them ever got a server and the other files ended up with no
 * connection at all. Keeping the mapping 1:1 also keeps the host's grammar pairing straight, which
 * is what stops a {@code .java} file from being highlighted with the Kotlin grammar.
 *
 * <p>kmp-lsp is a Rust/tree-sitter language server: no JVM, no Gradle import, so it answers
 * requests in a fresh workspace instead of after a project import. The plugin installs the server
 * and its native jar-indexer sidecar into {@code /opt/kmp-lsp} and exposes a thin wrapper at
 * {@code /usr/local/bin/kmp-lsp}; the providers only point the host at that wrapper.
 *
 * <p>kmp-lsp speaks JSON-RPC over stdio by default, so no arguments are forwarded.
 */
public abstract class KmpLspProvider implements LspServerProvider {

  private static final String GUEST_EXECUTABLE = "/usr/local/bin/kmp-lsp";

  /**
   * Priority these providers are registered with, and the value {@link #getPriority()} reports.
   *
   * <p>The store also ships Kotlin and Java LSP plugins that claim extensions kmp-lsp handles. With
   * a tie, a {@code .kt} file can land on the JVM-based Kotlin server instead - whose grammar still
   * highlights the file while providing no real intelligence on a phone, which looks exactly like
   * "highlighting works, completion does not". kmp-lsp covers Kotlin and Java itself, so it
   * registers well above the defaults and takes the files.
   */
  public static final int PRIORITY = 200;

  /**
   * kmp-lsp answers {@code initialize} before indexing starts - the background index reports
   * through {@code $/progress} - so the handshake itself is fast. The generous timeout is for
   * proot, not for the server: the first start on a phone pays for paging in the binary and
   * spawning the sidecar, which can outlast a short client-side connect timeout even though nothing
   * is wrong.
   */
  private static final int INITIALIZATION_TIMEOUT_MILLIS = 60_000;

  private final ProotProcessLauncher launcher;
  private final String extension;

  protected KmpLspProvider(ProotProcessLauncher launcher, String extension) {
    this.launcher = launcher;
    this.extension = extension;
  }

  /** True once the plugin's setup action has installed the wrapper into the rootfs. */
  public boolean isInstalled() {
    return launcher.isInstalled(GUEST_EXECUTABLE);
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
    return LspServerDefinition.builder(getId(), Set.of(extension), getDisplayName(), this::connect)
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
