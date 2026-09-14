package com.example.rustlsp;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/**
 * Routes {@code .rs} files to {@code rust-analyzer} running inside the host's proot rootfs.
 *
 * @author Ghost
 */
public final class RustLspProvider implements LspServerProvider {

  static final String GUEST_EXECUTABLE = "/root/.cargo/bin/rust-analyzer";
  private static final Set<String> EXTENSIONS = Set.of("rs");
  private static final String DISPLAY_NAME = "rust-analyzer";

  private final ProotProcessLauncher launcher;

  public RustLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  public boolean isInstalled() {
    return launcher.isInstalled(GUEST_EXECUTABLE);
  }

  @Override
  public String getId() {
    return "com.example.rustlsp";
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return EXTENSIONS.contains(request.extension());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    return LspServerDefinition.builder(getId(), EXTENSIONS, DISPLAY_NAME, this::connect)
        .initializationOptions(buildInitOptions(request))
        .configuration(buildConfiguration())
        .enableInlayHints(true)
        .enableSignatureHelp(true)
        .build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    File cargoRoot = findCargoRoot(request.projectRoot());
    String workingDir = cargoRoot != null
        ? cargoRoot.getAbsolutePath()
        : request.projectRoot().getAbsolutePath();
    return launcher.launch(workingDir, GUEST_EXECUTABLE, Collections.emptyList());
  }

  private static Object buildInitOptions(LspServerRequest request) {
    Map<String, Object> opts = new HashMap<>();
    opts.put("linkedProjects", Collections.emptyList());
    opts.put("rustcSource", null);
    opts.put("procMacroServer", null);
    return opts;
  }

  private static Object buildConfiguration() {
    Map<String, Object> rustAnalyzer = new HashMap<>();

    Map<String, Object> cargo = new HashMap<>();
    cargo.put("autoreload", true);
    cargo.put("buildScripts", Map.of("enable", true));
    rustAnalyzer.put("cargo", cargo);

    Map<String, Object> check = new HashMap<>();
    check.put("command", "check");
    check.put("allTargets", true);
    rustAnalyzer.put("check", check);

    Map<String, Object> diagnostics = new HashMap<>();
    diagnostics.put("enable", true);
    diagnostics.put("disabled", Collections.emptyList());
    diagnostics.put("enableExperimental", true);
    rustAnalyzer.put("diagnostics", diagnostics);

    Map<String, Object> inlayHints = new HashMap<>();
    inlayHints.put("typeHints", Map.of("enable", true));
    inlayHints.put("parameterHints", Map.of("enable", true));
    inlayHints.put("chainingHints", Map.of("enable", true));
    inlayHints.put("closingBraceHints", Map.of("enable", true, "minLines", 25));
    inlayHints.put("lifetimeElisionHints", Map.of("enable", "always", "useParameterNames", true));
    inlayHints.put("closureReturnTypeHints", Map.of("enable", "with_block"));
    inlayHints.put("bindingModeHints", Map.of("enable", true));
    rustAnalyzer.put("inlayHints", inlayHints);

    Map<String, Object> procMacro = new HashMap<>();
    procMacro.put("enable", true);
    rustAnalyzer.put("procMacro", procMacro);

    Map<String, Object> complete = new HashMap<>();
    complete.put("callable", Map.of("snippets", "fill_arguments"));
    complete.put("postfix", Map.of("enable", true));
    rustAnalyzer.put("complete", complete);

    Map<String, Object> hover = new HashMap<>();
    hover.put("runlinks", true);
    hover.put("documentation", Map.of("enable", true));
    hover.put("maxLiteralLength", 10000);
    rustAnalyzer.put("hover", hover);

    return Map.of("rust-analyzer", rustAnalyzer);
  }

  private static File findCargoRoot(File start) {
    File current = start;
    while (current != null) {
      if (new File(current, "Cargo.toml").exists()) {
        return current;
      }
      current = current.getParentFile();
    }
    return null;
  }
}
