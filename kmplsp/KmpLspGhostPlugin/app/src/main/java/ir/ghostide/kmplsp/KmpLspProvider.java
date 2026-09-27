package ir.ghostide.kmplsp;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * host resolves a provider per extension and wraps the definition it gets in a
 * {@code CustomLanguageServerDefinition(..., ext=<extension>)} of its own, so a definition that
 * claims several extensions does not map cleanly onto that: only one of them ever got a server,
 * and the other files ended up with no connection at all. Keeping the mapping 1:1 also keeps the
 * host's per-language grammar/definition pairing straight, which is what stops a {@code .java} file
 * from being highlighted with the Kotlin grammar.
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
   * <p>The host sorts the providers registered for an extension point by that number, descending,
   * and breaks ties separately — so the number passed to {@code register(...)} is what actually
   * decides, not the getter. Most plugins in the store register with 0, which means they all tie and
   * the winner is decided by the tiebreaker rather than by intent.
   *
   * <p>That matters here: the store also ships Kotlin and Java LSP plugins that claim extensions
   * kmp-lsp handles. With a tie, a {@code .kt} file can land on the JVM-based Kotlin server instead
   * — whose grammar still highlights the file while providing no real intelligence on a phone,
   * which looks exactly like "highlighting works, completion does not". kmp-lsp covers Kotlin and
   * Java itself, so it registers well above the defaults and takes the files.
   */
  public static final int PRIORITY = 200;

  /**
   * Passed through as {@code initializationOptions}. kmp-lsp reads
   * {@code indexingOptions.ignorePatterns} (gitignore-style globs, applied to both its {@code fd}
   * fast path and its directory-walk fallback) and honours them on the next start, so nothing else
   * is needed here. The defaults already skip {@code .git}, {@code build}, {@code target},
   * {@code .gradle}, {@code .build} and {@code DerivedData}; the extra entries cover the trees a
   * phone project tends to accumulate. Keeping the list short matters: every pattern that fails to
   * exclude a directory means tree-sitter parsing of every file inside it.
   */
  private static final List<String> IGNORE_PATTERNS =
      List.of(".idea", ".kotlin", "captures", "node_modules", "Pods", "out", "cmake-build-*");

  /** How deep under the project root a module's own jars are looked for. */
  private static final int JAR_SEARCH_DEPTH = 4;

  /**
   * kmp-lsp answers {@code initialize} before indexing starts — the background index reports through
   * {@code $/progress} — so the handshake itself is fast. The generous timeout is for proot, not for
   * the server: first start on a phone pays for unpacking a 19 MB binary, jemalloc arena setup,
   * tokio worker threads and spawning the sidecar, which can outlast a short client-side connect
   * timeout even though nothing is wrong.
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
        .initializationOptions(indexingOptions(request.projectRoot()))
        .enableInlayHints(true)
        .enableSignatureHelp(true)
        .initializationTimeoutMillis(INITIALIZATION_TIMEOUT_MILLIS)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), GUEST_EXECUTABLE, Collections.emptyList());
  }

  /**
   * {@code initializationOptions} for one project.
   *
   * <p>The project's own files need nothing: kmp-lsp walks the workspace root itself. Everything
   * that is not project code does, and there is exactly one list that can say so —
   * {@code indexingOptions.jarPaths}, the same list {@code workspace.json} carries.
   *
   * <p>It is what makes a Ghost IDE plugin's own API resolve. {@code app/libs/*.jar} and
   * {@code *.aar} hold every {@code ir.hanzodev1375.ghostide} class the code imports, and they are
   * in neither the Android SDK nor the Gradle cache, so without this list a {@code .java} file
   * imports {@code EditorHost} and gets nothing: no completion, no hover, no go-to-definition.
   *
   * <p>Entries stay relative to the project root on purpose. kmp-lsp resolves a relative path
   * against the workspace root it settled on, and inside proot that root is whatever the host
   * handed the launcher - an absolute path built on the Android side would not necessarily exist
   * there.
   */
  private Map<String, Object> indexingOptions(File projectRoot) {
    Map<String, Object> indexing = new LinkedHashMap<>();
    indexing.put("ignorePatterns", IGNORE_PATTERNS);
    List<String> jarPaths = jarPaths(projectRoot);
    if (!jarPaths.isEmpty()) {
      indexing.put("jarPaths", jarPaths);
    }
    return Map.of("indexingOptions", indexing);
  }

  /**
   * The project directories that hold jars, relative to {@code projectRoot} and in a stable order.
   *
   * <p>A directory qualifies when it holds at least one {@code .jar} or {@code .aar}: {@code libs}
   * of a module and the {@code build/libs} of a built one. Build and cache trees are skipped, and
   * a directory that does not exist yet is never reported - the list is rebuilt on every server
   * start, so a project that has not been built simply does not mention it.
   */
  private static List<String> jarPaths(File projectRoot) {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      return List.of();
    }
    Set<String> found = new LinkedHashSet<>();
    collectJarDirectories(projectRoot, projectRoot, 0, found);
    return List.copyOf(found);
  }

  private static void collectJarDirectories(File root, File directory, int depth, Set<String> found) {
    if (depth > JAR_SEARCH_DEPTH || !directory.isDirectory()) {
      return;
    }
    File[] children = directory.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (!child.isDirectory()) {
        continue;
      }
      String name = child.getName();
      if (name.equals(".gradle") || name.equals(".git")) {
        continue;
      }
      // A module's own jars: <module>/libs, and <module>/build/libs for one that has been built.
      // build/ is never descended into - it holds generated trees that are of no use here.
      if (name.equals("build")) {
        File built = new File(child, "libs");
        if (holdsJar(built)) {
          add(root, built, found);
        }
        continue;
      }
      if (name.equals("libs")) {
        if (holdsJar(child)) {
          add(root, child, found);
        }
        continue;
      }
      collectJarDirectories(root, child, depth + 1, found);
    }
  }

  private static void add(File root, File directory, Set<String> found) {
    String relative = relativize(root, directory);
    if (relative != null) {
      found.add(relative);
    }
  }

  private static boolean holdsJar(File directory) {
    File[] children = directory.listFiles();
    if (children == null) {
      return false;
    }
    for (File child : children) {
      String name = child.getName();
      if (child.isFile() && (name.endsWith(".jar") || name.endsWith(".aar"))) {
        return true;
      }
    }
    return false;
  }

  /** The workspace-relative form kmp-lsp resolves, or {@code null} when it is the root itself. */
  private static String relativize(File root, File directory) {
    String rootPath = root.getAbsolutePath();
    String path = directory.getAbsolutePath();
    if (path.length() <= rootPath.length() || !path.startsWith(rootPath)) {
      return null;
    }
    String relative = path.substring(rootPath.length());
    while (relative.startsWith(File.separator)) {
      relative = relative.substring(File.separator.length());
    }
    return relative.isEmpty() ? null : relative;
  }
}
