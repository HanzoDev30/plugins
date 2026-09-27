package ir.ghostide.androidbuilder;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Stages the shell assets of the plugin where the proot terminal can reach them, and turns them
 * into single line commands.
 *
 * <p>The host binds the app's files directory into every terminal session as {@code /ghostide}
 * (the same mechanism it uses for its own {@code /ghostide/files/shell} wrappers), so a script is
 * written once, on the Android side, and then simply executed by the terminal. Every command stays
 * on one line on purpose: the plugin manager can concatenate all setup actions with {@code &&},
 * which would break a multi line heredoc.
 */
final class PluginScripts {

  static final String INSTALL_JDK = "install-jdk.sh";
  static final String INSTALL_SDK = "install-sdk.sh";
  static final String CONFIGURE_GRADLE = "configure-gradle.sh";
  static final String PATCH_BUILD_TOOLS = "patch-build-tools.sh";
  static final String BUILD_APK = "build-apk.sh";
  static final String STOP_BUILD = "stop-build.sh";

  /**
   * Every command the panel hands to the terminal carries this. The scripts use it to keep quiet
   * about the things the panel shows itself: the build result box in the terminal would be a second
   * window for the event the panel already reports as an install dialog.
   */
  private static final String PLUGIN_UI = "ANDROIDBUILDER_PLUGIN_UI=1";
  static final String LIST_TASKS = "list-tasks.sh";

  /** Sourced, not executed: the project lookup and the task list shared by the two commands. */
  private static final String GRADLE_COMMON = "gradle-common.sh";

  /** Sourced: asks a host whether it is reachable, so a mirror is only used where it is needed. */
  private static final String NET_ROUTE = "net-route.sh";

  private static final String STAGE_DIR_NAME = "androidbuilder";
  private static final String PROOT_STAGE_DIR = "/ghostide/files/" + STAGE_DIR_NAME;
  private static final String FALLBACK_DIR = "/opt/android-builder";

  private final PluginContext plugin;

  PluginScripts(PluginContext plugin) {
    this.plugin = plugin;
  }

  /**
   * Copies a script asset into the plugin's files directory and returns the terminal command that
   * runs it, or {@code null} when the script could not be staged.
   */
  String command(String asset, String... arguments) {
    String body = readAsset(asset);
    if (body.isEmpty()) {
      return null;
    }
    List<String> companions = companions(asset);
    List<String> bodies = new ArrayList<>();
    for (String companion : companions) {
      String library = readAsset(companion);
      if (library.isEmpty()) {
        plugin.getLogger().error("Cannot read asset " + companion);
        return null;
      }
      bodies.add(library);
    }
    String tail = withArguments(arguments);

    List<String> staged = new ArrayList<>();
    boolean allStaged = true;
    for (int index = 0; index < companions.size(); index++) {
      String path = stage(companions.get(index), bodies.get(index));
      if (path == null) {
        allStaged = false;
        break;
      }
      staged.add(path);
    }
    String script = stage(asset, body);
    if (!allStaged || script == null) {
      return inline(asset, body, companions, bodies, tail);
    }
    return PLUGIN_UI + " bash " + script + tail + "\n";
  }

  /** Scripts that are {@code source}d rather than run, so they have to sit in the same directory. */
  private static List<String> companions(String asset) {
    if (BUILD_APK.equals(asset) || LIST_TASKS.equals(asset)) {
      return List.of(GRADLE_COMMON);
    }
    if (INSTALL_JDK.equals(asset)
        || INSTALL_SDK.equals(asset)
        || CONFIGURE_GRADLE.equals(asset)
        || PATCH_BUILD_TOOLS.equals(asset)) {
      return List.of(NET_ROUTE);
    }
    return List.of();
  }

  /** Terminal path of a staged script, or {@code null} if it is not on disk. */
  private String stage(String asset, String body) {
    try {
      Context context = plugin.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
      File directory = new File(context.getFilesDir(), STAGE_DIR_NAME);
      if (!directory.isDirectory() && !directory.mkdirs()) {
        return null;
      }
      File target = new File(directory, asset);
      try (FileOutputStream out = new FileOutputStream(target)) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
      target.setExecutable(true, false);
      return PROOT_STAGE_DIR + "/" + asset;
    } catch (IOException | RuntimeException e) {
      plugin.getLogger().error("Cannot stage " + asset, e);
      return null;
    }
  }

  /**
   * Fallback for the rare case where the Android side cannot write the scripts: they travel inside
   * the command as base64, which is still a single line.
   */
  private String inline(
      String asset, String body, List<String> companions, List<String> bodies, String tail) {
    StringBuilder command = new StringBuilder("mkdir -p ").append(FALLBACK_DIR);
    for (int index = 0; index < companions.size(); index++) {
      command
          .append(" && printf %s '")
          .append(base64(bodies.get(index)))
          .append("' | base64 -d > ")
          .append(FALLBACK_DIR)
          .append("/")
          .append(companions.get(index));
    }
    command
        .append(" && printf %s '")
        .append(base64(body))
        .append("' | base64 -d > ")
        .append(FALLBACK_DIR)
        .append("/")
        .append(asset)
        .append(" && ")
        .append(PLUGIN_UI)
        .append(" bash ")
        .append(FALLBACK_DIR)
        .append("/")
        .append(asset)
        .append(tail)
        .append("\n");
    return command.toString();
  }

  private static String base64(String body) {
    return android.util.Base64.encodeToString(
        body.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
  }

  private static String withArguments(String... arguments) {
    if (arguments == null || arguments.length == 0) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (String argument : arguments) {
      if (argument != null && !argument.isEmpty()) {
        parts.add(quote(argument));
      }
    }
    return parts.isEmpty() ? "" : " " + String.join(" ", parts);
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  private String readAsset(String asset) {
    try {
      Context context = plugin.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
      try (InputStream in = context.getAssets().open(asset)) {
        return new String(readAll(in), StandardCharsets.UTF_8);
      }
    } catch (IOException | RuntimeException e) {
      plugin.getLogger().error("Cannot read asset " + asset, e);
      return "";
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }
}
