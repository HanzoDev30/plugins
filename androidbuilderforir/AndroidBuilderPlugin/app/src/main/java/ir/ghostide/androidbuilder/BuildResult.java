package ir.ghostide.androidbuilder;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * The outcome of the last terminal build, as {@code build-apk.sh} left it in {@code last-result}.
 *
 * <p>The build runs in the proot terminal and the panel lives in the editor, so the only thing the
 * two share is that file: it sits in the host app's own files directory, which the terminal has bind
 * mounted as {@code /ghostide/files}.
 */
final class BuildResult {

  private static final String MARKER = "last-result";

  final String status;
  final String task;
  final String apk;
  final String summary;
  final String at;

  private BuildResult(Map<String, String> values) {
    this.status = value(values, "status");
    this.task = value(values, "task");
    this.apk = value(values, "apk");
    this.summary = value(values, "summary");
    this.at = value(values, "at");
  }

  static File markerFile(Context context) {
    return new File(new File(context.getFilesDir(), "androidbuilder"), MARKER);
  }

  /** The last result, or {@code null} when no build has finished (or the file is half written). */
  static BuildResult read(Context context) {
    File file = markerFile(context);
    if (!file.isFile()) {
      return null;
    }
    String body;
    try (InputStream in = Files.newInputStream(file.toPath())) {
      body = new String(readAll(in), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      return null;
    }
    Map<String, String> values = new HashMap<>();
    for (String line : body.split("\n")) {
      int split = line.indexOf('=');
      if (split > 0) {
        values.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
      }
    }
    // The script writes the timestamp last, so its absence means the write was interrupted.
    return values.containsKey("at") ? new BuildResult(values) : null;
  }

  boolean isRunning() {
    return "running".equals(status);
  }

  boolean isOk() {
    return "ok".equals(status);
  }

  boolean isFailure() {
    return "fail".equals(status);
  }

  /** The user stopped it: not a failure, and nothing to install either. */
  boolean isCancelled() {
    return "cancelled".equals(status);
  }

  boolean isFinished() {
    return isOk() || isFailure() || isCancelled();
  }

  /** The APK this build produced, if it is still on disk. */
  File apkFile() {
    return apk.isEmpty() ? null : new File(apk);
  }

  boolean hasApk() {
    File file = apkFile();
    return file != null && file.isFile();
  }

  private static String value(Map<String, String> values, String key) {
    String value = values.get(key);
    return value == null ? "" : value;
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int read;
    while ((read = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }
}
