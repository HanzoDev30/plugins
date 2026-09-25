package ir.ghostide.arduinolsp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

public final class ArduinoLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList(
          "/usr/local/bin/arduino-language-server",
          "/usr/bin/arduino-language-server",
          "/root/.local/bin/arduino-language-server");

  private static final List<String> SERVER_ARGS = List.of();

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("ino", "pde");

  private final ProotProcessLauncher launcher;
  private final ArduinoTextmateHost textmate;

  public ArduinoLspProvider(ProotProcessLauncher launcher, ArduinoTextmateHost textmate) {
    this.launcher = launcher;
    this.textmate = textmate;
  }

  @Override
  public String getId() {
    return "ir.ghostide.arduinolsp.lsp";
  }

  @Override
  public String getDisplayName() {
    return "Arduino Language Server";
  }

  public boolean isInstalled() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean supports(LspServerRequest request) {
    return request != null
        && request.extension() != null
        && SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase(Locale.ROOT));
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    LspServerDefinition.Builder builder =
        LspServerDefinition.builder(getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
            .grammarScopeName("source.arduino")
            .textMateGrammarLink(
                "https://raw.githubusercontent.com/microsoft/vscode/main/extensions/cpp/syntaxes/cpp.tmLanguage.json")
            .enableInlayHints(true)
            .enableSignatureHelp(true)
            .initializationTimeoutMillis(120_000);
    if (textmate != null) {
      textmate.registerScope("source.arduino");
    }
    return builder.build();
  }

  private LspServerConnection connect(LspServerRequest request) {
    return launcher.launch(
        request.projectRoot().getAbsolutePath(), findExecutable(), SERVER_ARGS);
  }

  private String findExecutable() {
    for (String path : CANDIDATE_PATHS) {
      if (launcher.isInstalled(path)) {
        return path;
      }
    }
    return CANDIDATE_PATHS.get(0);
  }
}