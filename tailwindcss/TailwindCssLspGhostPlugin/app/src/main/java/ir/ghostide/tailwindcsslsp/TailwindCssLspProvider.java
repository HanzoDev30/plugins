package ir.ghostide.tailwindcsslsp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ir.hanzodev1375.ghostide.ide.api.LspServerConnection;
import ir.hanzodev1375.ghostide.ide.api.LspServerDefinition;
import ir.hanzodev1375.ghostide.ide.api.LspServerProvider;
import ir.hanzodev1375.ghostide.ide.api.LspServerRequest;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

public final class TailwindCssLspProvider implements LspServerProvider {

  // Outranks the Bootstrap proxy (500) so, in a workspace that uses both, the
  // Tailwind proxy owns the HTML language server and its own utilities win.
  public static final int PRIORITY = 600;

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
  private final PluginLogger log;

  public TailwindCssLspProvider(ProotProcessLauncher launcher, PluginLogger log) {
    this.launcher = launcher;
    this.log = log;
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
    return PRIORITY;
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
    if (request == null || request.file() == null || request.extension() == null) {
      return false;
    }
    if (!SUPPORTED_EXTENSIONS.contains(request.extension().toLowerCase(Locale.ROOT))) {
      return false;
    }
    boolean ok = usesTailwind(request.file());
    if (log != null) {
      log.info("tailwind: supports " + request.file() + " -> " + ok);
    }
    return ok;
  }

  private static boolean usesTailwind(File file) {
    File dir = file.isDirectory() ? file : file.getParentFile();
    for (int depth = 0; dir != null && depth < 10; depth++, dir = dir.getParentFile()) {
      if (new File(dir, "node_modules/tailwindcss").isDirectory()) {
        return true;
      }
      if (new File(dir, "node_modules/@tailwindcss").isDirectory()) {
        return true;
      }
      for (String name : new String[] {
        "tailwind.config.js",
        "tailwind.config.ts",
        "tailwind.config.cjs",
        "tailwind.config.mjs",
        "tailwind.config.mts"
      }) {
        if (new File(dir, name).isFile()) {
          return true;
        }
      }
      File pkg = new File(dir, "package.json");
      if (pkg.isFile()) {
        try {
          String json = new String(Files.readAllBytes(pkg.toPath()), StandardCharsets.UTF_8);
          if (json.contains("\"tailwindcss\"")) {
            return true;
          }
        } catch (Exception ignored) {
          // keep walking up
        }
      }
    }
    return false;
  }

  @Override
  public LspServerDefinition createDefinition(LspServerRequest request) {
    if (log != null) {
      log.info(
          "tailwind: selected for "
              + (request != null && request.file() != null ? request.file() : "?")
              + " (root="
              + (request != null && request.projectRoot() != null
                  ? request.projectRoot().getAbsolutePath()
                  : "?")
              + ")");
    }
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
    try {
      String exe = findExecutable();
      String root =
          request != null && request.projectRoot() != null
              ? request.projectRoot().getAbsolutePath()
              : "/";
      if (log != null) {
        log.info("tailwind: launching " + exe + " in " + root);
      }
      return launcher.launch(root, exe, SERVER_ARGS);
    } catch (Throwable t) {
      if (log != null) {
        log.error("tailwind: failed to launch language server", t);
      }
      throw t;
    }
  }

  private String findExecutable() {
    StringBuilder seen = new StringBuilder();
    for (String path : CANDIDATE_PATHS) {
      boolean installed = launcher.isInstalled(path);
      seen.append(path).append(installed ? "=yes " : "=no ");
      if (installed) {
        return path;
      }
    }
    throw new IllegalStateException(
        "tailwindcss-language-server not installed; candidates: " + seen);
  }
}