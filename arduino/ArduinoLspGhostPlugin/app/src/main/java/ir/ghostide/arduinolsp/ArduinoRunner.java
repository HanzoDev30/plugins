package ir.ghostide.arduinolsp;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

public final class ArduinoRunner implements CodeRunnerHost {

  private static final Set<String> EXTENSIONS = Set.of("ino", "pde");
  private static final String FQBN = "arduino:avr:uno";

  private final PluginContext context;

  public ArduinoRunner(PluginContext context) {
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
      File sketchDir = new File(filePath).getParentFile();
      String dir = sketchDir != null ? shellQuote(sketchDir.getAbsolutePath()) : ".";
      String command =
          "if [ -x \"$HOME/.local/bin/arduino-cli\" ]; then "
              + "\"$HOME/.local/bin/arduino-cli\" compile --fqbn "
              + shellQuote(FQBN)
              + " "
              + dir
              + "; elif command -v arduino-cli >/dev/null 2>&1; then "
              + "arduino-cli compile --fqbn "
              + shellQuote(FQBN)
              + " "
              + dir
              + "; else echo 'arduino-cli not found; run the Arduino toolchain installer first.' >&2; exit 127; fi";
      host().runShell(command, asBottomSheet);
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
    return context.getServices().require(IdeHostServices.CODE_RUNNER_HOST);
  }

  private static String getExtension(String filePath) {
    int dot = filePath.lastIndexOf('.');
    if (dot < 0 || dot == filePath.length() - 1) return "";
    return filePath.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}