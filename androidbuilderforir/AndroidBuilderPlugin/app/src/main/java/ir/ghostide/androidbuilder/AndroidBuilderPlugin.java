package ir.ghostide.androidbuilder;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeEvents;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.PluginUiExtensionPoints;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * AndroidBuilder for ir - builds APKs inside Ghost IDE on Iranian networks.
 *
 * <p>The plugin contributes a bottom sheet panel (the UI) plus a handful of setup actions for the
 * plugin popup. Both paths only ever hand work to the real proot terminal: the scripts that
 * install the JDK, the SDK from {@code maven.myket.ir}, the Gradle mirror, the arm64 build-tools
 * and finally {@code ./gradlew} live as assets and are executed by {@code bash} inside the
 * terminal.
 *
 * <p>Nothing is registered as an {@code EDITOR_PANEL} path provider, so the editor's code runner
 * keeps running the file the user has open.
 *
 * <p>The project path shown in the panel is never stored: {@link IdeEvents#FILE_EVENT} is the hook
 * that re-reads it, so the field names the project of the tab in front of the user instead of
 * whatever a previous run happened to leave behind.
 */
public final class AndroidBuilderPlugin implements GhostPlugin {

  private PluginContext context;
  private PluginScripts scripts;
  private Disposable panelRegistration;
  private Disposable fileEventRegistration;

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    this.scripts = new PluginScripts(context);

    Context androidContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    AndroidBuilderPanel panel = new AndroidBuilderPanel(androidContext, context);
    panelRegistration =
        context
            .getExtensions()
            .register(
                PluginUiExtensionPoints.EDITOR_PANEL,
                panel,
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(panelRegistration);

    // Every tab that opens re-asks the host which project it belongs to.
    fileEventRegistration =
        context
            .getExtensions()
            .register(
                IdeEvents.FILE_EVENT,
                event -> panel.onFileEvent(event),
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(fileEventRegistration);

    context
        .getLogger()
        .info("AndroidBuilder for ir ready - open the plugin popup for the build panel");
  }

  @Override
  public void deactivate() {
    if (panelRegistration != null) {
      panelRegistration.dispose();
      panelRegistration = null;
    }
    if (fileEventRegistration != null) {
      fileEventRegistration.dispose();
      fileEventRegistration = null;
    }
    scripts = null;
    context = null;
  }

  @Override
  public List<PluginSetupAction> getSetupActions() {
    PluginScripts scripts = scripts();
    if (scripts == null) {
      return List.of();
    }
    List<PluginSetupAction> actions = new ArrayList<>();
    add(
        actions,
        scripts,
        "install-jdk",
        "Install JDK 17",
        PluginScripts.INSTALL_JDK,
        new String[0],
        "Installs OpenJDK 17 inside the proot Debian - 'bash gradlew' cannot start without a JVM.");
    add(
        actions,
        scripts,
        "install-sdk",
        "Install Android SDK (Iran mirror)",
        PluginScripts.INSTALL_SDK,
        new String[0],
        "Command line tools, platform-tools, build-tools and platforms from maven.myket.ir, each"
            + " archive verified against its sha1, plus the SDK licenses. The NDK is not installed"
            + " here, it has its own button.");
    add(
        actions,
        scripts,
        "install-ndk",
        "Install the NDK (optional)",
        PluginScripts.INSTALL_SDK,
        new String[] {"ndk"},
        "Only needed by projects that compile native code. The mirror ships Google's x86_64 NDK,"
            + " which cannot run on an arm64 phone; export ANDROIDBUILDER_NDK_URL to an aarch64"
            + " archive first if you want a usable one.");
    add(
        actions,
        scripts,
        "configure-gradle",
        "Configure Gradle (Iran mirror)",
        PluginScripts.CONFIGURE_GRADLE,
        new String[0],
        "Rewrites the Google Maven repositories of every build to maven.myket.ir and puts the"
            + " mirror first for plugins and buildscripts.");
    add(
        actions,
        scripts,
        "patch-build-tools",
        "Patch build-tools for ARM64",
        PluginScripts.PATCH_BUILD_TOOLS,
        new String[0],
        "Replaces the x86_64 binaries (aapt2, zipalign, ...) with arm64 ones and pins"
            + " android.aapt2FromMavenOverride, so Gradle never downloads Google's aapt2.");
    add(
        actions,
        scripts,
        "list-tasks",
        "List the project's Gradle tasks",
        PluginScripts.LIST_TASKS,
        new String[0],
        "Shows what the project can build - assemble/bundle/install/lint per variant and module -"
            + " the way Android Studio's Build menu does. Read from the project's own build"
            + " scripts, so flavors are included; nothing is compiled.");
    add(
        actions,
        scripts,
        "build-debug",
        "Build debug APK",
        PluginScripts.BUILD_APK,
        new String[] {"assembleDebug"},
        "Runs 'bash <project>/gradlew assembleDebug' in the project that owns the wrapper (/sdcard grants no exec bit, so it is never run as ./gradlew).");
    add(
        actions,
        scripts,
        "build-release",
        "Build release APK",
        PluginScripts.BUILD_APK,
        new String[] {"assembleRelease"},
        "Runs 'bash <project>/gradlew assembleRelease' in the project that owns the wrapper.");
    return List.copyOf(actions);
  }

  private PluginScripts scripts() {
    if (scripts == null && context != null) {
      scripts = new PluginScripts(context);
    }
    return scripts;
  }

  private static void add(
      List<PluginSetupAction> actions,
      PluginScripts scripts,
      String id,
      String label,
      String asset,
      String[] arguments,
      String description) {
    String command = scripts.command(asset, arguments);
    if (command != null) {
      actions.add(new PluginSetupAction(id, label, command, description));
    }
  }
}
