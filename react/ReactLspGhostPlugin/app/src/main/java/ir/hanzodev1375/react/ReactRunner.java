package ir.hanzodev1375.react;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Runs React code inside the IDE terminal. A detected React project starts its own dev script; a
 * standalone component gets a throwaway Vite harness next to it; anything else is bundled with
 * esbuild and executed with Node.
 */
public final class ReactRunner {

  private static final String HARNESS_DIR = ".react-run";
  private static final String ENTRY_BASE = "UserComponent";

  private final PluginContext context;

  public ReactRunner(PluginContext context) {
    this.context = context;
  }

  /** Entry point used by the toolbar icon and the command. Safe to call from any thread. */
  public void run() {
    Thread worker = new Thread(this::detectAndRun, "react-runner");
    worker.setDaemon(true);
    worker.start();
  }

  private void detectAndRun() {
    EditorHost editor = context.getServices().require(IdeHostServices.EDITOR_HOST);
    File openFile = editor.getOpenFile();
    File projectRoot = editor.getProjectRoot();

    File reactRoot = ReactProject.findRoot(openFile, projectRoot);
    if (reactRoot != null) {
      runProject(reactRoot);
      return;
    }

    if (openFile != null && ReactProject.isJsxSource(ReactProject.extension(openFile.getName()))) {
      runFile(openFile, projectRoot);
      return;
    }

    toast("فایل یا پروژهٔ React پیدا نشد؛ یک فایل .jsx/.tsx یا پروژهٔ React باز کن");
  }

  void runProject(File root) {
    String script = ReactProject.pickDevScript(root);
    if (script == null) {
      toast("در package.json اسکریپتی برای اجرا (dev/start) پیدا نشد");
      return;
    }
    String pm = ReactProject.packageManager(root);
    String command = projectCommand(root, pm, script);
    context.getLogger()
        .info("react: running " + root + " in a Linux workspace via " + pm + " run " + script);
    toast("اجرای پروژهٔ React با «" + pm + " run " + script + "» در ترمینال");
    runShell(command);
  }

  void runFile(File file, File projectRoot) {
    if (looksLikeComponent(file)) {
      runComponentHarness(file, projectRoot);
    } else {
      bundleAndRun(file, projectRoot);
    }
  }

  private void runComponentHarness(File file, File projectRoot) {
    File base = projectRoot != null ? projectRoot : file.getParentFile();
    if (base == null) {
      toast("مسیر فایل مشخص نیست");
      return;
    }
    File staging = new File(base, HARNESS_DIR + "/single-" + safeName(file.getName()));
    try {
      writeHarness(staging, file);
    } catch (IOException e) {
      context.getLogger().error("react: cannot create harness", e);
      toast("ساخت پروژهٔ موقت ناموفق بود: " + e.getMessage());
      return;
    }
    String command = harnessCommand(staging);
    context.getLogger().info("react: harness staged at " + staging);
    toast("ساخت پیش‌نمایش React و اجرای Vite در ترمینال");
    runShell(command);
  }

  private void bundleAndRun(File file, File projectRoot) {
    File outDir = projectRoot != null ? projectRoot : file.getParentFile();
    if (outDir == null) {
      toast("مسیر فایل مشخص نیست");
      return;
    }
    File out = new File(outDir, HARNESS_DIR + "/bundle/" + safeName(file.getName()) + ".mjs");
    File parent = out.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    String command =
        "cd "
            + ReactProject.quote(file.getParentFile().getAbsolutePath())
            + " && npx --yes esbuild "
            + ReactProject.quote(file.getName())
            + " --bundle --platform=node --format=esm --jsx=automatic --outfile="
            + ReactProject.quote(out.getAbsolutePath())
            + " && node "
            + ReactProject.quote(out.getAbsolutePath());
    context.getLogger().info("react: bundling " + file);
    toast("باندل و اجرای فایل با esbuild در ترمینال");
    runShell(command);
  }

  /**
   * React projects must be installed and run inside the Linux rootfs: the shared storage mount is
   * {@code noexec} and cannot create symlinks, so {@code node_modules} cannot live under
   * {@code /sdcard}. The sources are copied into a persistent workspace under the proot home, where
   * install scripts, bin links and native binaries (esbuild, rollup) all work.
   */
  private static String projectCommand(File project, String pm, String script) {
    String proj = ReactProject.quote(project.getAbsolutePath());
    return "PROJ="
        + proj
        + " && WORK="
        + workspace(project)
        + " && mkdir -p \"$WORK\" && "
        + "cp -rf \"$PROJ/.\" \"$WORK/\" && "
        + "{ [ -d \"$PROJ/node_modules\" ] || rm -rf \"$WORK/node_modules\"; } && "
        + "cd \"$WORK\" && { [ -d node_modules ] || "
        + installCommand(pm)
        + "; } && "
        + pm
        + " run "
        + script;
  }

  private static String harnessCommand(File staging) {
    return "SRC="
        + ReactProject.quote(staging.getAbsolutePath())
        + " && WORK="
        + workspace(staging)
        + " && mkdir -p \"$WORK\" && "
        + "cp -rf \"$SRC/.\" \"$WORK/\" && rm -rf \"$SRC\" && "
        + "cd \"$WORK\" && { [ -d node_modules ] || "
        + installCommand("npm")
        + "; } && npm run dev";
  }

  private static String installCommand(String pm) {
    switch (pm) {
      case "pnpm":
        return "pnpm install";
      case "yarn":
        return "yarn install";
      case "bun":
        return "bun install";
      default:
        return "npm install --no-audit --no-fund";
    }
  }

  /** A persistent workspace under the proot home, keyed by the source path. */
  private static String workspace(File dir) {
    String name = safeName(dir.getName());
    if (name.isEmpty()) {
      name = "project";
    }
    String key = Integer.toHexString(dir.getAbsolutePath().hashCode());
    return "\"${HOME:-/root}/.react-run/" + name + "-" + key + "\"";
  }

  private void runShell(String command) {
    CodeRunnerHost runner = context.getServices().require(IdeHostServices.CODE_RUNNER_HOST);
    runner.runShell(command, true);
  }

  private static boolean looksLikeComponent(File file) {
    String ext = ReactProject.extension(file.getName());
    if ("jsx".equals(ext) || "tsx".equals(ext)) {
      return true;
    }
    try {
      String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      String lower = text.toLowerCase(Locale.ROOT);
      return lower.contains("from 'react'")
          || lower.contains("from \"react\"")
          || lower.contains("react/jsx-runtime")
          || (lower.contains("react") && lower.contains("<") && lower.contains("=>"));
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  private void writeHarness(File harness, File component) throws IOException {
    if (!harness.exists() && !harness.mkdirs()) {
      throw new IOException("cannot create " + harness.getAbsolutePath());
    }
    String ext = ReactProject.extension(component.getName());
    String entryName = ENTRY_BASE + "." + (ext.isEmpty() ? "jsx" : ext);

    Map<String, String> files = new LinkedHashMap<>();
    files.put("package.json", HARNESS_PACKAGE_JSON);
    files.put("vite.config.mjs", VITE_CONFIG);
    files.put("index.html", INDEX_HTML);
    files.put("src/main.jsx", mainEntry(entryName));
    for (Map.Entry<String, String> e : files.entrySet()) {
      writeFile(new File(harness, e.getKey()), e.getValue());
    }
    Files.copy(
        component.toPath(),
        new File(harness, "src/" + entryName).toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private static String mainEntry(String entryName) {
    String importPath = "./" + entryName.replaceAll("\\.[^.]+$", "");
    return "import React from 'react'\n"
        + "import { createRoot } from 'react-dom/client'\n"
        + "import * as mod from '"
        + importPath
        + "'\n\n"
        + "const candidate = mod.default ?? Object.values(mod).find((v) => typeof v === 'function')\n"
        + "const App = candidate ?? (() => <div style={{ padding: 24 }}>No component export found</div>)\n\n"
        + "createRoot(document.getElementById('root')).render(<App />)\n";
  }

  private static void writeFile(File file, String content) throws IOException {
    File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("cannot create " + parent.getAbsolutePath());
    }
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }

  private static String safeName(String name) {
    String base = name.replaceAll("\\.[^.]+$", "");
    String safe = base.replaceAll("[^a-zA-Z0-9_-]", "-");
    return safe.isEmpty() ? "component" : safe;
  }

  private void toast(String message) {
    UiFeedbackHost ui = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (ui != null) {
      ui.toast(message, true);
    }
    PluginLogger logger = context.getLogger();
    if (logger != null) {
      logger.info("react: " + message);
    }
  }

  private static final String HARNESS_PACKAGE_JSON =
      "{\n"
          + "  \"name\": \"ghost-react-run\",\n"
          + "  \"private\": true,\n"
          + "  \"type\": \"module\",\n"
          + "  \"scripts\": { \"dev\": \"vite\", \"build\": \"vite build\" },\n"
          + "  \"dependencies\": {\n"
          + "    \"react\": \"^18.3.1\",\n"
          + "    \"react-dom\": \"^18.3.1\"\n"
          + "  },\n"
          + "  \"devDependencies\": {\n"
          + "    \"@vitejs/plugin-react\": \"^4.3.4\",\n"
          + "    \"vite\": \"^5.4.11\",\n"
          + "    \"typescript\": \"^5.6.3\"\n"
          + "  }\n"
          + "}\n";

  private static final String VITE_CONFIG =
      "import { defineConfig } from 'vite'\n"
          + "import react from '@vitejs/plugin-react'\n\n"
          + "export default defineConfig({\n"
          + "  plugins: [react()],\n"
          + "  server: { host: true, port: 5173, strictPort: false },\n"
          + "})\n";

  private static final String INDEX_HTML =
      "<!doctype html>\n"
          + "<html lang=\"en\">\n"
          + "  <head>\n"
          + "    <meta charset=\"utf-8\" />\n"
          + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n"
          + "    <title>React Preview</title>\n"
          + "  </head>\n"
          + "  <body>\n"
          + "    <div id=\"root\"></div>\n"
          + "    <script type=\"module\" src=\"/src/main.jsx\"></script>\n"
          + "  </body>\n"
          + "</html>\n";
}
