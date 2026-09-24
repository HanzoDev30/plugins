package ir.ghostide.rlsp;

import java.util.Set;
import java.util.function.Consumer;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * R code runner implementing {@link CodeRunnerHost}.
 *
 * <p>For {@code .R/.Rmd/.Rnw} files runs {@code Rscript <file>} in the host terminal (proot
 * rootfs). For every other file type delegates to the host's default runner.
 *
 * @author Ghost
 */
public final class RRunner implements CodeRunnerHost {

  private static final Set<String> EXTENSIONS = Set.of("r", "rmd", "rnw");

  private final PluginContext context;

  public RRunner(PluginContext context) {
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
      host().runShell("Rscript " + shellEscape(filePath), asBottomSheet);
    } else {
      host().runFile(filePath, asBottomSheet);
    }
  }

  @Override
  public boolean isSupported(String filePath) {
    if (filePath == null) return false;
    return EXTENSIONS.contains(getExtension(filePath));
  }

  @Override
  public ExecResult exec(String command, Consumer<String> onOutputLine) {
    return host().exec(command, onOutputLine);
  }

  private CodeRunnerHost host() {
    return context.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
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