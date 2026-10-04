package ir.hanzodev1375.angular;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/** @angular/language-server (ngserver). Only claims files that live inside an Angular workspace. */
public final class AngularLspProvider implements LspServerProvider {

  static final int PRIORITY = 400;

  private static final String WRAPPER = "/usr/local/bin/angular-language-server";
  private static final String INSTALL_DIR = "/opt/angular-lsp";
  private static final String NODE = "/usr/bin/node";
  private static final String PACKAGE_DIR = INSTALL_DIR + "/node_modules/@angular/language-server";
  private static final String RAW_ENTRY = PACKAGE_DIR + "/bin/ngserver";

  /** ts: inline templates and components, html: external templates. */
  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("ts", "html");

  private static final List<String> WORKSPACE_MARKERS = List.of("angular.json", ".angular.json");
  private static final int MAX_PARENT_LEVELS = 8;

  private final ProotProcessLauncher launcher;
  private final PluginLogger logger;

  public AngularLspProvider(ProotProcessLauncher launcher, PluginLogger logger) {
    this.launcher = launcher;
    this.logger = logger;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.angular.lsp";
  }

  @Override
  public String getDisplayName() {
    return "Angular Language Server (@angular/language-server)";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    if (request == null || request.file() == null) {
      return false;
    }
    if (!SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase(Locale.ROOT))) {
      return false;
    }
    // Priority 400 would otherwise hijack every plain .ts/.html file, so require a workspace.
    return findWorkspaceRoot(request) != null;
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    logger.info("angular: lsp definition for " + request.file().getName());
    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .enableInlayHints(false)
        .enableSignatureHelp(true)
        .initializationTimeoutMillis(120_000)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    File workspace = findWorkspaceRoot(request);
    String cwd =
        (workspace != null ? workspace : request.projectRoot()).getAbsolutePath();
    String probes = String.join(",", probeLocations(request, workspace));
    List<String> serverArgs =
        List.of("--stdio", "--tsProbeLocations", probes, "--ngProbeLocations", probes);

    if (launcher.isInstalled(WRAPPER)) {
      logger.info("angular: launching " + WRAPPER + " " + serverArgs + " in " + cwd);
      return launcher.launch(cwd, WRAPPER, serverArgs);
    }
    if (launcher.isInstalled(NODE) && launcher.isInstalled(RAW_ENTRY)) {
      List<String> args = new ArrayList<>();
      args.add(RAW_ENTRY);
      args.addAll(serverArgs);
      logger.info("angular: launching " + NODE + " " + args + " in " + cwd);
      return launcher.launch(cwd, NODE, args);
    }
    logger.error(
        "angular: no usable Angular language server (wrapper=" + probe(WRAPPER) + ", node="
            + probe(NODE) + ", entry=" + probe(RAW_ENTRY) + ")",
        null);
    throw new IllegalStateException(
        "Angular language server is not installed, run the plugin setup action first");
  }

  /** The project's own typescript/@angular/language-service first, the private install last. */
  private static List<String> probeLocations(LspServerRequest request, File workspace) {
    Set<String> dirs = new LinkedHashSet<>();
    if (workspace != null) {
      dirs.add(workspace.getAbsolutePath() + "/node_modules");
    }
    if (request.projectRoot() != null) {
      dirs.add(request.projectRoot().getAbsolutePath() + "/node_modules");
    }
    dirs.add(INSTALL_DIR + "/node_modules");
    return new ArrayList<>(dirs);
  }

  /** Nearest directory above the file (up to the project root's parents) holding angular.json. */
  static File findWorkspaceRoot(LspServerRequest request) {
    File dir = request.file().getParentFile();
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++) {
      for (String marker : WORKSPACE_MARKERS) {
        if (new File(dir, marker).isFile()) {
          return dir;
        }
      }
      dir = dir.getParentFile();
    }
    File root = request.projectRoot();
    if (root != null) {
      for (String marker : WORKSPACE_MARKERS) {
        if (new File(root, marker).isFile()) {
          return root;
        }
      }
    }
    return null;
  }

  private boolean probe(String path) {
    try {
      return launcher.isInstalled(path);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
