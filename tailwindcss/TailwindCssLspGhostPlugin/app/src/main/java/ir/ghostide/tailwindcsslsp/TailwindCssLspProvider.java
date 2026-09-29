package ir.ghostide.tailwindcsslsp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

public final class TailwindCssLspProvider implements LspServerProvider {

  private static final List<String> CANDIDATE_PATHS =
      Arrays.asList(
          "/usr/local/bin/tailwindcss-language-server-wrapper",
          "/usr/bin/tailwindcss-language-server",
          "/usr/local/bin/tailwindcss-language-server");

  private static final List<String> SERVER_ARGS = List.of();

  private static final Set<String> SUPPORTED_EXTENSIONS =
      Set.of(
          "css",
          "jsx",
          "tsx",
          "js",
          "ts",
          "vue",
          "svelte",
          "astro",
          "php",
          "erb",
          "haml",
          "slim");

  private static final Map<String, Object> TAILWIND_CONFIG =
      Map.ofEntries(
          Map.entry("classAttributes", List.of("class", "className", "ngClass", "class:list")),
          Map.entry("classFunctions", List.of("clsx", "cva", "cn", "twMerge", "tv")),
          Map.entry("includeLanguages", Map.of("html", "html", "blade", "html")),
          Map.entry("validate", true),
          Map.entry(
              "lint",
              Map.ofEntries(
                  Map.entry("cssConflict", "warning"),
                  Map.entry("invalidApply", "error"),
                  Map.entry("invalidScreen", "error"),
                  Map.entry("invalidVariant", "error"),
                  Map.entry("invalidConfigPath", "error"),
                  Map.entry("invalidTailwindDirective", "error"),
                  Map.entry("recommendedVariantOrder", "warning"))));

  private static final Map<String, Object> USER_LANGUAGES =
      Map.ofEntries(
          Map.entry("html", "html"),
          Map.entry("css", "css"),
          Map.entry("javascript", "javascript"),
          Map.entry("javascriptreact", "javascriptreact"),
          Map.entry("typescript", "typescript"),
          Map.entry("typescriptreact", "typescriptreact"),
          Map.entry("vue", "vue"),
          Map.entry("svelte", "svelte"),
          Map.entry("astro", "astro"),
          Map.entry("php", "html"),
          Map.entry("blade", "html"),
          Map.entry("erb", "html"));

  private final ProotProcessLauncher launcher;

  public TailwindCssLspProvider(ProotProcessLauncher launcher) {
    this.launcher = launcher;
  }

  @Override
  public String getId() {
    return "ir.ghostide.tailwindcsslsp";
  }

  @Override
  public String getDisplayName() {
    return "Tailwind CSS Language Server (@tailwindcss/language-server)";
  }

  @Override
  public int getPriority() {
    return 300;
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
    if (request == null || request.extension() == null) {
      return false;
    }
    return SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase());
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    Map<String, Object> initOptions =
        Map.of("userLanguages", USER_LANGUAGES, "tailwindCSS", TAILWIND_CONFIG);

    Map<String, Object> configuration = Map.of("tailwindCSS", TAILWIND_CONFIG);

    return LspServerDefinition.builder(
            getId(), SUPPORTED_EXTENSIONS, getDisplayName(), this::connect)
        .initializationOptions(initOptions)
        .configuration(configuration)
        .initializationTimeoutMillis(20_000)
        .enableInlayHints(true)
        .enableSignatureHelp(true)
        .build();
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
    throw new IllegalStateException(
        "tailwindcss-language-server not found in: " + CANDIDATE_PATHS);
  }
}