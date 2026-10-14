package ir.hanzodev1375.react;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Reads React projects: root detection, dev script and package manager. */
final class ReactProject {

  private static final int MAX_PARENT_LEVELS = 14;

  private static final String[] DEPENDENCY_SECTIONS = {
    "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
  };

  private static final String[] PREFERRED_SCRIPTS = {"dev", "start", "serve", "develop"};

  private static final String[] SERVER_MARKERS = {
    "vite", "react-scripts", "next", "parcel", "rsbuild", "webpack serve"
  };

  private ReactProject() {}

  static File findRoot(File start, File projectRoot) {
    File dir = start;
    if (dir != null && !dir.isDirectory()) {
      dir = dir.getParentFile();
    }
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++, dir = dir.getParentFile()) {
      if (isReactProject(dir)) {
        return dir;
      }
    }
    for (File candidate = projectRoot;
        candidate != null;
        candidate = candidate.getParentFile()) {
      if (isReactProject(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  static boolean isReactProject(File dir) {
    if (dir == null) {
      return false;
    }
    JSONObject json = readPackageJson(dir);
    return json != null && hasReactDependency(json);
  }

  static JSONObject readPackageJson(File dir) {
    if (dir == null) {
      return null;
    }
    File packageJson = new File(dir, "package.json");
    if (!packageJson.isFile()) {
      return null;
    }
    try {
      String text = new String(Files.readAllBytes(packageJson.toPath()), StandardCharsets.UTF_8);
      return new JSONObject(text);
    } catch (IOException | JSONException | RuntimeException e) {
      return null;
    }
  }

  private static boolean hasReactDependency(JSONObject json) {
    for (String section : DEPENDENCY_SECTIONS) {
      JSONObject deps = json.optJSONObject(section);
      if (deps == null) {
        continue;
      }
      for (Iterator<String> it = deps.keys(); it.hasNext(); ) {
        if ("react".equals(it.next())) {
          return true;
        }
      }
    }
    return false;
  }

  /** The npm script that starts a dev server, or {@code null} when none is obvious. */
  static String pickDevScript(File root) {
    JSONObject json = readPackageJson(root);
    if (json == null) {
      return null;
    }
    JSONObject scripts = json.optJSONObject("scripts");
    if (scripts == null) {
      return null;
    }
    for (String name : PREFERRED_SCRIPTS) {
      if (scripts.has(name)) {
        return name;
      }
    }
    List<String> keys = new ArrayList<>();
    for (Iterator<String> it = scripts.keys(); it.hasNext(); ) {
      keys.add(it.next());
    }
    for (String marker : SERVER_MARKERS) {
      for (String key : keys) {
        String command = scripts.optString(key, "").toLowerCase(Locale.ROOT);
        if (command.contains(marker)) {
          return key;
        }
      }
    }
    return null;
  }

  static String packageManager(File root) {
    if (root == null) {
      return "npm";
    }
    if (new File(root, "pnpm-lock.yaml").isFile()) {
      return "pnpm";
    }
    if (new File(root, "yarn.lock").isFile()) {
      return "yarn";
    }
    if (new File(root, "bun.lockb").isFile() || new File(root, "bun.lock").isFile()) {
      return "bun";
    }
    return "npm";
  }

  static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 || dot == name.length() - 1
        ? ""
        : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static boolean isJsxSource(String extension) {
    switch (extension) {
      case "jsx":
      case "tsx":
      case "js":
      case "ts":
      case "mjs":
      case "cjs":
        return true;
      default:
        return false;
    }
  }

  /** Quotes a path for safe use inside a {@code sh -c} command. */
  static String quote(String path) {
    return "'" + path.replace("'", "'\\''") + "'";
  }
}
