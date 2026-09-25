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
    String tail = withArguments(arguments);
    String staged = stage(asset, body);
    return staged == null ? inline(asset, body, tail) : "bash " + staged + tail + "\n";
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
   * Fallback for the rare case where the Android side cannot write the script: the script travels
   * inside the command as base64, which is still a single line.
   */
  private String inline(String asset, String body, String tail) {
    String payload = android.util.Base64.encodeToString(
        body.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    String path = FALLBACK_DIR + "/" + asset;
    return "mkdir -p "
        + FALLBACK_DIR
        + " && printf %s '"
        + payload
        + "' | base64 -d > "
        + path
        + " && bash "
        + path
        + tail
        + "\n";
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
