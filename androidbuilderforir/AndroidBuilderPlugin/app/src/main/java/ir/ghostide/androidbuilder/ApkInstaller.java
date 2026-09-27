package ir.ghostide.androidbuilder;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Installs a freshly built APK, using the app's own context.
 *
 * <p>There is no {@code adb} to call: the SDK's platform-tools are x86_64 binaries that cannot run
 * on an arm64 phone, and the proot terminal has no {@code pm}. So the install is done by the app
 * itself, in three steps, each needing less from the host than the last:
 *
 * <ol>
 *   <li>a package installer session, which is the system install dialog, but only works if the host
 *       app holds {@code REQUEST_INSTALL_PACKAGES};
 *   <li>the same dialog through a MediaStore uri, which needs no permission at all, at the price of
 *       a copy of the APK in the Downloads folder;
 *   <li>{@code su -c pm install}, which on a rooted phone installs without any dialog.
 * </ol>
 */
final class ApkInstaller {

  private static final String MIME = "application/vnd.android.package-archive";
  private static final int CHUNK = 64 * 1024;

  interface Callback {
    /** Called off the main thread. */
    void onResult(boolean handedOver, String note);
  }

  private ApkInstaller() {}

  static void install(Context context, File apk, Callback callback) {
    Context app = context.getApplicationContext();
    new Thread(
            () -> {
              String note = "";
              boolean handedOver = false;

              try {
                installBySession(app, apk);
                handedOver = true;
              } catch (Throwable failure) {
                note = describe(failure);
              }

              if (!handedOver) {
                try {
                  installByViewer(app, apk);
                  handedOver = true;
                } catch (Throwable failure) {
                  note = describe(failure);
                }
              }

              if (!handedOver) {
                try {
                  String output = installAsRoot(apk);
                  handedOver = output.contains("Success");
                  note = handedOver ? "" : lastLine(output);
                } catch (Throwable failure) {
                  note = describe(failure);
                }
              }

              callback.onResult(handedOver, note);
            },
            "androidbuilder-install")
        .start();
  }

  /** The system installer, driven by a session. Throws when the host may not install packages. */
  private static void installBySession(Context context, File apk) throws IOException {
    PackageInstaller installer = context.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams params =
        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    int session = installer.createSession(params);
    boolean committed = false;
    try (PackageInstaller.Session open = installer.openSession(session)) {
      try (OutputStream out = open.openWrite("base.apk", 0, apk.length());
          InputStream in = new FileInputStream(apk)) {
        copy(in, out);
        open.fsync(out);
      }
      Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
      install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
      install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      PendingIntent pending =
          PendingIntent.getActivity(
              context,
              session,
              install,
              PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      open.commit(pending.getIntentSender());
      committed = true;
    } finally {
      if (!committed) {
        try {
          installer.abandonSession(session);
        } catch (RuntimeException ignored) {
          // Nothing to clean up.
        }
      }
    }
  }

  /**
   * The system installer, reached with a content uri. The APK is published in Downloads first,
   * because Android 7 refuses a {@code file://} uri from another app and the plugin cannot add a
   * FileProvider to somebody else's manifest.
   */
  private static void installByViewer(Context context, File apk) throws IOException {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      throw new IOException("Android 10 or newer is needed for this route");
    }
    ContentValues values = new ContentValues();
    values.put(MediaStore.Downloads.DISPLAY_NAME, apk.getName());
    values.put(MediaStore.Downloads.MIME_TYPE, MIME);
    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
    values.put(MediaStore.Downloads.IS_PENDING, 1);

    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) {
      throw new IOException("Downloads did not take the file");
    }
    boolean published = false;
    try {
      try (OutputStream out = context.getContentResolver().openOutputStream(uri);
          InputStream in = new FileInputStream(apk)) {
        if (out == null) {
          throw new IOException("cannot write " + uri);
        }
        copy(in, out);
      }
      ContentValues done = new ContentValues();
      done.put(MediaStore.Downloads.IS_PENDING, 0);
      context.getContentResolver().update(uri, done, null, null);
      published = true;
    } finally {
      if (!published) {
        try {
          context.getContentResolver().delete(uri, null, null);
        } catch (RuntimeException ignored) {
          // The pending entry disappears on its own eventually.
        }
      }
    }

    Intent view = new Intent(Intent.ACTION_VIEW);
    view.setDataAndType(uri, MIME);
    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(view);
  }

  /** {@code pm install} as root: no dialog, and nothing needed from the manifest. */
  private static String installAsRoot(File apk) throws IOException, InterruptedException {
    String path = "'" + apk.getAbsolutePath().replace("'", "'\\''") + "'";
    String throughSu = exec(new String[] {"su", "-c", "pm install -r " + path});
    if (throughSu.contains("Success")) {
      return throughSu;
    }
    String direct = exec(new String[] {"pm", "install", "-r", apk.getAbsolutePath()});
    return direct.contains("Success") ? direct : throughSu + " / " + direct;
  }

  private static String exec(String[] command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output;
    try (InputStream in = process.getInputStream()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[CHUNK];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
    process.waitFor();
    return output;
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] chunk = new byte[CHUNK];
    int read;
    while ((read = in.read(chunk)) != -1) {
      out.write(chunk, 0, read);
    }
  }

  private static String describe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isEmpty() ? failure.getClass().getSimpleName() : message;
  }

  private static String lastLine(String text) {
    String[] lines = text.split("\n");
    for (int index = lines.length - 1; index >= 0; index--) {
      String line = lines[index].trim();
      if (!line.isEmpty()) {
        return line;
      }
    }
    return "";
  }
}
