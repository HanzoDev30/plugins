package ir.hanzodev30.nodeui;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ProotShell {

  private static final String PROOT_LIBRARY_NAME = "libproot.so";
  private static final String LOADER_LIBRARY_NAME = "libloader.so";
  private static final String LOADER32_LIBRARY_NAME = "libloader32.so";
  private static final String ROOTFS_RELATIVE = "rootfs/debian";

  private ProotShell() {}

  interface LineListener {
    void onLine(String line);
  }

  static final class Result {
    final int exitCode;
    final String output;

    Result(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    boolean isSuccess() {
      return exitCode == 0;
    }
  }

  static File rootfsDir(Context context) {
    return new File(context.getFilesDir(), ROOTFS_RELATIVE);
  }

  static boolean isInstalled(Context context) {
    return new File(rootfsDir(context), "bin/bash").exists();
  }

  static boolean hasBinary(Context context, String guestPath) {
    File rootfs = rootfsDir(context);
    String relative = guestPath.startsWith("/") ? guestPath.substring(1) : guestPath;
    return new File(rootfs, relative).exists();
  }

  static Result run(Context context, String command, String workDir, LineListener listener)
      throws IOException, InterruptedException {
    File rootfs = rootfsDir(context);
    if (!rootfs.isDirectory()) {
      throw new IOException(
          context.getString(R.string.nodeui_error_no_rootfs) + rootfs.getAbsolutePath());
    }

    String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
    File prootBinary = new File(nativeLibDir, PROOT_LIBRARY_NAME);
    if (!prootBinary.exists()) {
      throw new IOException("libproot.so not found: " + prootBinary.getAbsolutePath());
    }
    File loaderBinary = new File(nativeLibDir, LOADER_LIBRARY_NAME);
    if (!loaderBinary.exists()) {
      throw new IOException("libloader.so not found: " + loaderBinary.getAbsolutePath());
    }

    File tmpDir = new File(context.getCacheDir(), "nodeui-tmp/" + System.nanoTime());
    if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) {
      throw new IOException("cannot create temp dir: " + tmpDir.getAbsolutePath());
    }

    String work = workDir == null || workDir.isEmpty() ? "/root" : workDir;

    List<String> argv = new ArrayList<>();
    argv.add(prootBinary.getAbsolutePath());
    argv.add("--kill-on-exit");
    argv.add("-0");
    argv.add("--link2symlink");
    argv.add("-r");
    argv.add(rootfs.getAbsolutePath());
    argv.add("-b");
    argv.add("/dev");
    argv.add("-b");
    argv.add("/proc");
    argv.add("-b");
    argv.add("/sys");
    argv.add("-b");
    argv.add(context.getFilesDir().getParentFile().getAbsolutePath());
    argv.add("-b");
    argv.add("/storage/emulated/0");
    argv.add("-w");
    argv.add(work);
    argv.add("/bin/bash");
    argv.add("-lc");
    argv.add(command);

    ProcessBuilder pb = new ProcessBuilder(argv);
    pb.redirectErrorStream(true);
    pb.environment().clear();
    pb.environment().put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
    pb.environment().put("PROOT_LOADER", loaderBinary.getAbsolutePath());
    pb.environment().put("LD_LIBRARY_PATH", nativeLibDir);
    File loader32 = new File(nativeLibDir, LOADER32_LIBRARY_NAME);
    if (loader32.exists()) {
      pb.environment().put("PROOT_LOADER_32", loader32.getAbsolutePath());
    }
    pb.environment()
        .put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin");
    pb.environment().put("HOME", "/root");
    pb.environment().put("TERM", "xterm-256color");
    pb.environment().put("LANG", "C.UTF-8");
    pb.environment().put("LC_ALL", "C.UTF-8");
    pb.environment().put("npm_config_update_notifier", "false");
    pb.environment().put("npm_config_fund", "false");
    pb.environment().put("npm_config_audit", "false");

    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append('\n');
        if (listener != null) {
          listener.onLine(line);
        }
      }
    }
    int exitCode = process.waitFor();
    deleteRecursive(tmpDir);
    return new Result(exitCode, output.toString());
  }

  private static void deleteRecursive(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursive(child);
      }
    }
    file.delete();
  }
}
