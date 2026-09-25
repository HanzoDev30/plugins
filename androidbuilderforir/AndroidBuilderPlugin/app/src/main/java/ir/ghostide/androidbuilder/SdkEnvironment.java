package ir.ghostide.androidbuilder;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Reads the state of the Android tool chain straight from the proot rootfs, so the panel can show
 * what is installed before anything is started.
 *
 * <p>The terminal runs a Debian rootfs under the app's files directory with {@code HOME=/root}, so
 * {@code ~/Android/sdk} on the phone is {@code <filesDir>/rootfs/debian/root/Android/sdk} here and
 * the sdkmanager is at {@code <filesDir>/rootfs/debian/root/Android/sdk/cmdline-tools/latest}.
 */
final class SdkEnvironment {

  private static final String ROOTFS = "rootfs/debian";

  private final File rootfs;
  private final File sdk;

  SdkEnvironment(PluginContext plugin) {
    Context context = plugin.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    this.rootfs = new File(context.getFilesDir(), ROOTFS);
    this.sdk = new File(new File(rootfs, "root/Android"), "sdk");
  }

  boolean hasRootfs() {
    return new File(rootfs, "bin/bash").isFile();
  }

  String sdkPath() {
    return "$HOME/Android/sdk";
  }

  boolean hasJdk() {
    return jdkDirectory() != null;
  }

  String jdkLabel() {
    File jdk = jdkDirectory();
    if (jdk == null) {
      return "نصب نیست - پروژه بدون JVM بیلد نمی‌شود";
    }
    return jdk.getName();
  }

  private File jdkDirectory() {
    File[] candidates =
        listChildren(new File(rootfs, "usr/lib/jvm"));
    if (candidates == null) {
      return null;
    }
    for (File candidate : candidates) {
      if (new File(candidate, "bin/javac").isFile()) {
        return candidate;
      }
    }
    return null;
  }

  boolean hasSdk() {
    return hasBuildTools() || !platforms().isEmpty();
  }

  /** The 130 MB command line tools are optional, so they are reported apart. */
  boolean hasCommandLineTools() {
    return new File(sdk, "cmdline-tools/latest/bin/sdkmanager").isFile();
  }

  boolean hasBuildTools() {
    return !directories(sdk, "build-tools").isEmpty();
  }

  List<String> buildTools() {
    return directories(sdk, "build-tools");
  }

  List<String> platforms() {
    return directories(sdk, "platforms");
  }

  List<String> ndk() {
    return directories(sdk, "ndk");
  }

  List<String> cmake() {
    return directories(sdk, "cmake");
  }

  /** Number of build-tools that already carry the arm64 replacement. */
  int patchedBuildTools() {
    int patched = 0;
    for (String version : buildTools()) {
      if (new File(new File(sdk, "build-tools/" + version), ".patched").isFile()) {
        patched++;
      }
    }
    return patched;
  }

  /** True once {@code ~/.gradle/init.gradle} sends Google Maven through the Iranian mirror. */
  boolean gradleMirrorReady() {
    File init = new File(rootfs, "root/.gradle/init.gradle");
    if (!init.isFile()) {
      return false;
    }
    try {
      return new String(java.nio.file.Files.readAllBytes(init.toPath())).contains("maven.myket.ir");
    } catch (Exception e) {
      return false;
    }
  }

  boolean aaptOverrideReady() {
    File properties = new File(rootfs, "root/.gradle/gradle.properties");
    if (!properties.isFile()) {
      return false;
    }
    try {
      return new String(java.nio.file.Files.readAllBytes(properties.toPath()))
          .contains("android.aapt2FromMavenOverride");
    } catch (Exception e) {
      return false;
    }
  }

  /** Ready to build: a JVM, the SDK, at least one build-tools and the mirror in place. */
  boolean readyToBuild() {
    return hasRootfs() && hasJdk() && hasBuildTools();
  }

  /** Everything a first build needs, ignoring the optional NDK and CMake. */
  List<String> missingForBuild() {
    List<String> missing = new ArrayList<>();
    if (!hasRootfs()) {
      missing.add("proot (ترمینال هنوز بوت نشده)");
    }
    if (!hasJdk()) {
      missing.add("JDK 17");
    }
    if (!hasSdk()) {
      missing.add("Android SDK");
    }
    if (!hasBuildTools()) {
      missing.add("build-tools");
    }
    if (!gradleMirrorReady()) {
      missing.add("آینه Gradle");
    }
    return missing;
  }

  private static File[] listChildren(File directory) {
    File[] children = directory.listFiles(File::isDirectory);
    if (children == null) {
      return new File[0];
    }
    Arrays.sort(children);
    return children;
  }

  private static List<String> directories(File parent, String name) {
    File[] children = listChildren(new File(parent, name));
    if (children.length == 0) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<>(children.length);
    for (File child : children) {
      names.add(child.getName());
    }
    return names;
  }
}
