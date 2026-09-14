package com.example.rustlsp;

import java.io.File;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Rust code runner implementing {@link CodeRunnerHost}.
 *
 * <p>For {@code .rs} files detects whether the file belongs to a Cargo project and runs
 * accordingly:
 * <ul>
 *   <li>Cargo project ({@code Cargo.toml} found) &rarr; {@code cargo run}
 *   <li>Standalone file &rarr; {@code rustc} compile + execute
 * </ul>
 *
 * <p>For non-Rust files delegates to the host's {@link CodeRunnerHost}.
 *
 * @author Ghost
 */
public final class RustRunner implements CodeRunnerHost {

  private static final Set<String> EXTENSIONS = Set.of("rs");
  private static final String BUILD_DIR = "/root/rust-output";

  private final PluginContext context;

  public RustRunner(PluginContext context) {
    this.context = context;
  }

  @Override
  public void runShell(String command, boolean asBottomSheet) {
    host().runShell(command, asBottomSheet);
  }

  @Override
  public void runCurrentFile(boolean asBottomSheet) {
    host().runCurrentFile(asBottomSheet);
  }

  @Override
  public void runFile(String filePath, boolean asBottomSheet) {
    if (isSupported(filePath)) {
      String cargoRoot = findCargoRoot(filePath);
      if (cargoRoot != null) {
        host().runShell("cd " + shellEscape(cargoRoot) + " && cargo run", asBottomSheet);
      } else {
        String outBin = BUILD_DIR + "/output";
        String command =
            "mkdir -p "
                + BUILD_DIR
                + " && rustc "
                + shellEscape(filePath)
                + " -o "
                + outBin
                + " && "
                + outBin;
        host().runShell(command, asBottomSheet);
      }
    } else {
      host().runFile(filePath, asBottomSheet);
    }
  }

  @Override
  public boolean isSupported(String filePath) {
    if (filePath == null) return false;
    return EXTENSIONS.contains(getExtension(filePath));
  }

  private CodeRunnerHost host() {
    return context.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
  }

  private static String findCargoRoot(String filePath) {
    File current = new File(filePath).getParentFile();
    while (current != null) {
      if (new File(current, "Cargo.toml").exists()) {
        return current.getAbsolutePath();
      }
      current = current.getParentFile();
    }
    return null;
  }

  private static String getExtension(String filePath) {
    int dot = filePath.lastIndexOf('.');
    if (dot < 0 || dot == filePath.length() - 1) return "";
    return filePath.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  private static String shellEscape(String path) {
    return "\"" + path.replace("\"", "\\\"") + "\"";
  }
}
