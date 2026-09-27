package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The panel of AndroidBuilder for ir.
 *
 * <p>Every button hands its work to the real proot terminal: the plugin never builds anything on
 * its own, it decides what to run and shows what is already installed. The optional parts of the
 * SDK (NDK, CMake) are separate buttons, so nobody downloads a gigabyte they never asked for.
 *
 * <p>All the text lives in {@code res/values/strings.xml}: the context the host hands over resolves
 * the plugin's own resources, so the panel is translatable like any other app.
 */
final class AndroidBuilderView {

  private static final String KEY_TASK = "task";
  private static final String KEY_FLAGS = "flags";

  /** How many parents the live project walk may climb before it gives up. */
  private static final int UP_WALK = 12;

  /**
   * A Gradle task is a name, not a shell fragment: only the characters Gradle itself allows, so a
   * typo in the custom field is reported as a typo and never reaches the terminal as a command.
   */
  private static final String TASK_SHAPE = "[A-Za-z0-9:_.-]+";

  /**
   * A Gradle flag, not a shell fragment: a dash or a double dash, a name, and an optional
   * {@code =value} of harmless characters. Everything the panel itself insists on is allowed
   * (--info, --offline, --no-build-cache, --max-workers=2, -Pkey=value, -Dprop=value), and nothing
   * that could be a second command, a redirect or a variable is. A value always needs its
   * {@code =}: {@code -P key=value} would be read as a task name by the wrapper.
   */
  private static final String FLAG_SHAPE = "-{1,2}[A-Za-z][A-Za-z0-9_.-]*(=[A-Za-z0-9_./:@,+=~-]*)?";

  /** A flag line is a few words, not a paragraph: anything longer is a paste that went wrong. */
  private static final int MAX_FLAGS_LENGTH = 200;

  // A deep slate panel with an emerald accent: one green for everything that starts work, a deeper
  // green for the second build action, and bright semantic colours for the status column. The
  // surfaces step up in lightness instead of going translucent, so the panel keeps its shape on top
  // of the host's glass sheet instead of dissolving into it.
  private static int hex(String value) {
    return Color.parseColor(value);
  }

  // ── the palette ────────────────────────────────────────────────────────────────
  // Deep slate surfaces with an emerald accent, and one hue per section so the panel reads as
  // five small areas instead of one grey wall of text. Every colour is written as plain hex.
  private static final int ACCENT = hex("#2DD4A7"); // buttons that start work
  private static final int ACCENT_DEEP = hex("#12A37E"); // the second build action
  private static final int INK = hex("#04231B"); // dark label on a bright accent

  private static final int SKY = hex("#7DD3FC");
  private static final int VIOLET = hex("#C4B5FD");
  private static final int AMBER = hex("#FCD34D");
  private static final int EMERALD = hex("#34D399");
  private static final int TEAL = hex("#5EEAD4");

  private static final int OK = hex("#34E39B");
  private static final int BUSY = hex("#56B6FF");
  private static final int FAILED = hex("#FF6B81");
  private static final int IDLE = hex("#6E86A6"); // present but optional
  private static final int STOP_INK = hex("#FF6B81");
  private static final int STOP_FILL = hex("#241A1E");
  private static final int STOP_EDGE = hex("#4DFF6B81");

  // Text: tinted rather than pure white, so even the labels carry a colour.
  private static final int TEXT = hex("#E6EDF6");
  private static final int LABEL = hex("#A5C4E4"); // row titles
  private static final int NOTE = hex("#8FB8E0"); // help lines
  private static final int HINT = hex("#5C7A99"); // placeholders

  // Surfaces: the host's glass sheet shows through the scroll view, the cards and the buttons are
  // solid so they keep their shape instead of dissolving into the blur behind them.
  private static final int SURFACE = hex("#131A24");
  private static final int STRONG = hex("#1C2531");
  private static final int STROKE = hex("#26FFFFFF");
  private static final int RIPPLE = hex("#33FFFFFF");
  private static final int RIPPLE_ON_ACCENT = hex("#4D000000");

  private final Context context;
  private final PluginContext plugin;
  private final SdkEnvironment env;
  private final PluginScripts scripts;
  private final TerminalLauncher launcher;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final BuildWatcher watcher;
  private final View root;

  private final EditText projectField;
  private final EditText taskField;
  private final EditText flagsField;
  private final TextView[] values = new TextView[8];

  AndroidBuilderView(Context context, PluginContext plugin, SharedPreferences preferences) {
    this.context = context;
    this.plugin = plugin;
    this.env = new SdkEnvironment(plugin);
    this.scripts = new PluginScripts(plugin);
    this.launcher = new TerminalLauncher(plugin, context);
    this.watcher = new BuildWatcher(context, preferences, this::onBuildFinished);

    int text = TEXT;
    int body = LABEL;
    int note = NOTE;
    int hint = HINT;
    int surface = SURFACE;
    int strong = STRONG;
    int stroke = STROKE;
    int ripple = RIPPLE;

    this.projectField = new EditText(context);
    // The project path is never stored: it is read from the tab the user has open, every time.
    projectField.setHint(text(R.string.project_hint));
    projectField.setHintTextColor(hint);
    projectField.setTextColor(text);
    projectField.setTextSize(13);
    projectField.setSingleLine(true);
    projectField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    projectField.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
    projectField.setBackground(pressable(strong, stroke, ripple, 12));

    this.taskField = new EditText(context);
    taskField.setText(preferences.getString(KEY_TASK, ""));
    taskField.setHint(text(R.string.custom_task_hint));
    taskField.setHintTextColor(hint);
    taskField.setTextColor(text);
    taskField.setTextSize(13);
    taskField.setSingleLine(true);
    // The keyboard that fits a task name: no URI hints, and the action key runs it.
    taskField.setInputType(InputType.TYPE_CLASS_TEXT);
    taskField.setImeOptions(EditorInfo.IME_ACTION_GO);
    taskField.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
    taskField.setBackground(pressable(strong, stroke, ripple, 12));
    taskField.setOnEditorActionListener(
        (view, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_GO) {
            runCustomTask();
            return true;
          }
          return false;
        });
    taskField.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            preferences.edit().putString(KEY_TASK, s.toString().trim()).apply();
          }
        });

    this.flagsField = new EditText(context);
    flagsField.setText(preferences.getString(KEY_FLAGS, ""));
    flagsField.setHint(text(R.string.custom_flags_hint));
    flagsField.setHintTextColor(hint);
    flagsField.setTextColor(text);
    flagsField.setTextSize(13);
    flagsField.setSingleLine(true);
    // Plain text with no URI keyboard: a flag is a word, not a location.
    flagsField.setInputType(InputType.TYPE_CLASS_TEXT);
    flagsField.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
    flagsField.setBackground(pressable(strong, stroke, ripple, 12));
    flagsField.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            preferences.edit().putString(KEY_FLAGS, s.toString().trim()).apply();
          }
        });

    LinearLayout content = column();
    content.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 24));
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    titles.addView(label(context, text(R.string.app_title), 19, EMERALD, true));
    titles.addView(label(context, text(R.string.app_subtitle), 12, SKY, false));
    header.addView(
        titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(
        button(
            context, text(R.string.action_refresh), 12, SKY, strong, stroke, ripple,
            v -> refresh()),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    content.addView(header, gap(context, 18));

    content.addView(section(context, text(R.string.section_status), SKY));
    LinearLayout statusCard = card(context, surface, stroke);
    String checking = text(R.string.status_checking);
    for (int index = 0; index < values.length; index++) {
      statusCard.addView(
          row(
              context, text, body, index,
              text(
                  new int[] {
                    R.string.status_proot,
                    R.string.status_jdk,
                    R.string.status_sdk,
                    R.string.status_build_tools,
                    R.string.status_platforms,
                    R.string.status_mirror,
                    R.string.status_optional,
                    R.string.status_last_build
                  }[index]),
              checking),
          rowGap(context, index == values.length - 1 ? 0 : 7));
    }
    content.addView(statusCard, gap(context, 18));

    content.addView(section(context, text(R.string.section_project), VIOLET));
    LinearLayout projectCard = card(context, surface, stroke);
    projectCard.addView(label(context, text(R.string.project_label), 12, VIOLET, false));
    LinearLayout projectRow = new LinearLayout(context);
    projectRow.setOrientation(LinearLayout.HORIZONTAL);
    projectRow.setGravity(Gravity.CENTER_VERTICAL);
    projectRow.addView(
        projectField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    projectRow.addView(space(context, 10));
    projectRow.addView(
        button(
            context, text(R.string.action_detect), 13, VIOLET, strong, stroke, ripple,
            v -> detectProject()),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    projectCard.addView(projectRow, gap(context, 8));
    projectCard.addView(label(context, text(R.string.project_help), 11, note, false));
    content.addView(projectCard, gap(context, 18));

    content.addView(section(context, text(R.string.section_setup), AMBER));
    LinearLayout setup = column();
    setup.addView(
        buttonRow(
            context, strong, stroke, ripple, SKY,
            text(R.string.action_install_jdk), v -> run(PluginScripts.INSTALL_JDK),
            text(R.string.action_install_sdk), v -> run(PluginScripts.INSTALL_SDK)),
        rowGap(context, 9));
    setup.addView(
        buttonRow(
            context, strong, stroke, ripple, VIOLET,
            text(R.string.action_install_ndk), v -> run(PluginScripts.INSTALL_SDK, "ndk"),
            text(R.string.action_install_cmake), v -> run(PluginScripts.INSTALL_SDK, "cmake")),
        rowGap(context, 9));
    setup.addView(
        buttonRow(
            context, strong, stroke, ripple, AMBER,
            text(R.string.action_configure_gradle), v -> run(PluginScripts.CONFIGURE_GRADLE),
            text(R.string.action_patch_build_tools), v -> run(PluginScripts.PATCH_BUILD_TOOLS)),
        rowGap(context, 9));
    setup.addView(
        button(
            context,
            env.hasCommandLineTools()
                ? text(R.string.action_sdkmanager_ready)
                : text(R.string.action_install_sdkmanager),
            12, TEAL, strong, stroke, ripple,
            v -> run(PluginScripts.INSTALL_SDK, "tools")));
    content.addView(setup, gap(context, 20));

    content.addView(section(context, text(R.string.section_build), EMERALD));
    LinearLayout build = new LinearLayout(context);
    build.setOrientation(LinearLayout.HORIZONTAL);
    build.addView(
        button(
            context, text(R.string.action_build_debug), 15, INK, ACCENT, 0,
            RIPPLE_ON_ACCENT, v -> build("assembleDebug")),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    build.addView(space(context, 10));
    build.addView(
        button(
            context, text(R.string.action_build_release), 15, INK, ACCENT_DEEP, 0,
            RIPPLE_ON_ACCENT, v -> build("assembleRelease")),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    content.addView(build, gap(context, 9));
    content.addView(
        button(
            context, text(R.string.action_stop_build), 13, STOP_INK, STOP_FILL, STOP_EDGE, ripple,
            v -> stopBuild()),
        gap(context, 9));

    LinearLayout extras = new LinearLayout(context);
    extras.setOrientation(LinearLayout.HORIZONTAL);
    extras.addView(
        button(
            context, text(R.string.action_list_tasks), 13, SKY, strong, stroke, ripple,
            v -> listTasks()),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    extras.addView(space(context, 10));
    extras.addView(
        button(
            context, text(R.string.action_install_last), 13, EMERALD, strong, stroke, ripple,
            v -> installLast()),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    content.addView(extras, gap(context, 16));
    content.addView(label(context, text(R.string.build_help), 11, note, false));

    content.addView(section(context, text(R.string.section_custom_task), TEAL));
    LinearLayout taskCard = card(context, surface, stroke);
    taskCard.addView(label(context, text(R.string.custom_task_label), 12, TEAL, false));
    LinearLayout taskRow = new LinearLayout(context);
    taskRow.setOrientation(LinearLayout.HORIZONTAL);
    taskRow.setGravity(Gravity.CENTER_VERTICAL);
    taskRow.addView(
        taskField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    taskRow.addView(space(context, 10));
    taskRow.addView(
        button(
            context, text(R.string.action_run_task), 13, INK, ACCENT, 0,
            RIPPLE_ON_ACCENT, v -> runCustomTask()),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    taskCard.addView(taskRow, gap(context, 8));
    taskCard.addView(label(context, text(R.string.custom_task_help), 11, note, false));
    content.addView(taskCard, gap(context, 18));

    content.addView(section(context, text(R.string.section_custom_flags), VIOLET));
    LinearLayout flagsCard = card(context, surface, stroke);
    flagsCard.addView(label(context, text(R.string.custom_flags_label), 12, VIOLET, false));
    // No button of its own: the flags ride along with every build and with the task list, which is
    // what --info and --offline are for in the first place.
    flagsCard.addView(flagsField, gap(context, 8));
    flagsCard.addView(label(context, text(R.string.custom_flags_help), 11, note, false));
    content.addView(flagsCard);

    ScrollView scroll = new ScrollView(context);
    // The host's glass sheet is the background: the panel only paints its own cards.
    scroll.setBackgroundColor(Color.TRANSPARENT);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.addView(
        content,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    this.root = scroll;
    refresh();
  }

  View getRoot() {
    return root;
  }

  /** Re-reads the rootfs and updates the status card. */
  void refresh() {
    // The panel is opened again on every tab, so this is where a project that changed on the way
    // in is picked up.
    syncProject();
    String notInstalled = text(R.string.status_not_installed);
    set(0, env.hasRootfs() ? text(R.string.status_rootfs_ready) : text(R.string.status_rootfs_missing),
        env.hasRootfs() ? OK : IDLE);
    set(1, env.hasJdk() ? env.jdkLabel() : notInstalled, env.hasJdk() ? OK : IDLE);
    set(2, env.hasSdk() ? env.sdkPath() : notInstalled, env.hasSdk() ? OK : IDLE);
    set(3, env.buildTools().isEmpty() ? notInstalled : join(env.buildTools()),
        env.hasBuildTools() ? OK : IDLE);
    set(4, env.platforms().isEmpty() ? notInstalled : join(env.platforms()),
        env.platforms().isEmpty() ? IDLE : OK);
    set(5,
        env.gradleMirrorReady() ? text(R.string.status_mirror_ready) : text(R.string.status_mirror_missing),
        env.gradleMirrorReady() ? OK : IDLE);
    set(6, optionalLabel(), (!env.ndk().isEmpty() || !env.cmake().isEmpty()) ? OK : IDLE);
    showLastBuild();
    // A build that finished while the panel was closed is reported now.
    watcher.checkPending();
  }

  // ── actions ────────────────────────────────────────────────────────────────────

  /**
   * The plugin's own toast: the host draws it, so it looks like part of the IDE instead of a system
   * notification that belongs to no one. Falls back to a real toast when the host has no feedback
   * channel, which is the only case worth a system window.
   */
  private void toast(String message) {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message);
      return;
    }
    launcher.toast(message);
  }

  private void run(String asset, String... arguments) {
    String command = scripts.command(asset, arguments);
    if (command == null) {
      toast(text(R.string.toast_script_missing, asset));
      return;
    }
    launcher.run(command);
  }

  /** Hands the build to the terminal and starts waiting for the result marker it will write. */
  private void startBuild(String command) {
    if (command == null || command.trim().isEmpty()) {
      return;
    }
    launcher.run(command);
    watcher.arm();
  }

  /**
   * Stops a build that is still running. The build owns the first terminal, so this goes to a second
   * one: proot sessions share the process list, which is all the stop script needs.
   */
  private void stopBuild() {
    String command = scripts.command(PluginScripts.STOP_BUILD, runningTask());
    if (command == null) {
      toast(text(R.string.toast_script_missing, PluginScripts.STOP_BUILD));
      return;
    }
    // The script writes the outcome; the panel must not also raise a dialog for a build the user
    // just cancelled.
    watcher.stop();
    toast(text(R.string.toast_stopping));
    launcher.run(command);
  }

  /** The task that is running right now, for the result file the stop script writes. */
  private String runningTask() {
    BuildResult result = BuildResult.read(context);
    if (result != null && result.isRunning() && !result.task.isEmpty()) {
      return result.task;
    }
    return "";
  }

  private void build(String task) {
    // Checked before the environment is even looked at: a flag line that is not flags is a typo,
    // and the user should hear about that instead of watching a terminal install half the SDK.
    String flags = flags();
    if (flags == null) {
      return;
    }
    List<SdkEnvironment.Missing> missing = env.missingForBuild();
    if (missing.isEmpty()) {
      startBuild(scripts.command(PluginScripts.BUILD_APK, task, project(), flags));
      return;
    }
    askToInstall(missing, task, flags);
  }

  /**
   * The custom flag line, checked once so that nothing downstream has to think about it.
   *
   * @return the flags as a single argument for the scripts, an empty string when the field is
   *     empty, or {@code null} when the field is not a flag line - in which case the caller runs
   *     nothing at all.
   */
  private String flags() {
    String raw = flagsField.getText().toString().trim();
    if (raw.isEmpty()) {
      return "";
    }
    if (raw.length() > MAX_FLAGS_LENGTH) {
      toast(text(R.string.toast_flags_long, MAX_FLAGS_LENGTH));
      return null;
    }
    for (String token : raw.split("\\s+")) {
      if (!token.matches(FLAG_SHAPE)) {
        toast(text(R.string.toast_flag_shape, token));
        return null;
      }
    }
    return raw;
  }

  /** "List the project's Gradle tasks", with the same flags the build would use. */
  private void listTasks() {
    String flags = flags();
    if (flags == null) {
      return;
    }
    run(PluginScripts.LIST_TASKS, project(), flags);
  }

  /** The custom field: any Gradle task, with the same environment check and the same result dialog. */
  private void runCustomTask() {
    String task = task();
    if (task.isEmpty()) {
      toast(text(R.string.toast_no_task));
      return;
    }
    if (!task.matches(TASK_SHAPE)) {
      toast(text(R.string.toast_task_shape));
      return;
    }
    build(task);
  }

  private String task() {
    return taskField.getText().toString().trim();
  }

  private void askToInstall(List<SdkEnvironment.Missing> missing, String task, String flags) {
    StringBuilder message = new StringBuilder();
    for (SdkEnvironment.Missing step : missing) {
      message.append("• ").append(text(step.label)).append('\n');
    }
    message.append('\n').append(text(R.string.dialog_env_incomplete_question));

    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback == null) {
      runInstall(missing, task, flags);
      return;
    }
    feedback.confirm(
        text(R.string.dialog_env_incomplete),
        message.toString().trim(),
        accepted -> {
          if (accepted) {
            runInstall(missing, task, flags);
          }
        });
  }

  /** Chains the missing steps and the build into a single terminal session. */
  private void runInstall(List<SdkEnvironment.Missing> missing, String task, String flags) {
    List<String> steps = new ArrayList<>();
    for (SdkEnvironment.Missing step : missing) {
      if (step.script == null) {
        continue;
      }
      String command =
          step.argument == null
              ? scripts.command(step.script)
              : scripts.command(step.script, step.argument);
      if (command != null) {
        steps.add(command);
      }
    }
    if (env.hasBuildTools() && env.patchedBuildTools() == 0) {
      steps.add(scripts.command(PluginScripts.PATCH_BUILD_TOOLS));
    }
    String build = scripts.command(PluginScripts.BUILD_APK, task, project(), flags);
    if (build == null || hasEmptyStep(steps)) {
      toast(text(R.string.toast_scripts_incomplete));
      return;
    }
    steps.add(build.trim());
    startBuild(String.join(" && ", steps));
  }

  /** True when any staged command is missing. */
  private static boolean hasEmptyStep(List<String> steps) {
    for (String step : steps) {
      if (step == null || step.trim().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  // ── the build result ──────────────────────────────────────────────────────────

  /** The status row: what the last terminal build left behind. */
  private void showLastBuild() {
    BuildResult result = BuildResult.read(context);
    if (result == null) {
      set(7, text(R.string.status_last_build_none), IDLE);
    } else if (result.isRunning()) {
      set(7, text(R.string.status_last_build_running, result.task), BUSY);
    } else if (result.isOk() && result.hasApk()) {
      set(7, text(R.string.status_last_build_ok, result.task, size(result.apkFile())), OK);
    } else if (result.isOk()) {
      set(7, text(R.string.status_last_build_no_apk), IDLE);
    } else if (result.isCancelled()) {
      set(7, text(R.string.status_last_build_cancelled, result.task), IDLE);
    } else {
      set(7, text(R.string.status_last_build_failed, firstLine(result.summary)), FAILED);
    }
  }

  /**
   * The build runs in the terminal, so this is where it comes back: a finished build turns into the
   * install dialog, a failed one into its reason.
   */
  private void onBuildFinished(BuildResult result) {
    showLastBuild();
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);

    if (result.isCancelled()) {
      // The user asked for it: the row already says so, and there is nothing to ask about.
      return;
    }

    if (!result.isOk()) {
      String reason =
          result.summary.isEmpty() ? text(R.string.reason_unknown) : result.summary;
      if (feedback == null) {
        toast(text(R.string.toast_build_failed, firstLine(reason)));
        return;
      }
      feedback.confirm(
          text(R.string.dialog_build_failed),
          text(
              R.string.dialog_build_failed_message,
              result.task,
              reason,
              text(R.string.proot_log_path)),
          accepted -> {});
      return;
    }

    File apk = result.apkFile();
    if (apk == null || !apk.isFile()) {
      if (feedback == null) {
        toast(text(R.string.toast_build_ok_no_apk));
        return;
      }
      feedback.confirm(
          text(R.string.dialog_build_done_no_apk),
          text(R.string.dialog_build_done_no_apk_message, result.apk),
          accepted -> {});
      return;
    }

    if (feedback == null) {
      install(apk);
      return;
    }
    feedback.confirm(
        text(R.string.dialog_build_ok),
        text(R.string.dialog_build_ok_message, result.task, apk.getName(), size(apk)),
        accepted -> {
          if (accepted) {
            install(apk);
          }
        });
  }

  /** The "install the last APK" button: no dialog, the user already said so. */
  private void installLast() {
    BuildResult result = BuildResult.read(context);
    File apk = result == null ? null : result.apkFile();
    if (apk == null || !apk.isFile()) {
      toast(text(R.string.toast_no_apk_yet));
      return;
    }
    install(apk);
  }

  private void install(File apk) {
    toast(text(R.string.toast_installing, apk.getName()));
    ApkInstaller.install(
        context,
        apk,
        (handedOver, note) ->
            main.post(
                () -> {
                  if (handedOver) {
                    toast(text(R.string.toast_install_handed_over));
                    return;
                  }
                  String reason =
                      note.isEmpty() ? "" : text(R.string.dialog_install_failed_reason, note);
                  UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
                  if (feedback == null) {
                    toast(text(R.string.toast_install_failed, firstLine(note)));
                    return;
                  }
                  feedback.confirm(
                      text(R.string.dialog_install_failed),
                      text(
                          R.string.dialog_install_failed_message,
                          reason,
                          apk.getAbsolutePath()),
                      accepted -> {});
                }));
  }

  // ── project ────────────────────────────────────────────────────────────────────

  private void detectProject() {
    String detected = guessProject();
    if (detected == null) {
      // A tab that is open but owns no wrapper is a different story from an empty editor, and the
      // user can act on the first one and not on the second.
      EditorHost host = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
      toast(
          text(
              host != null && host.getOpenFile() != null
                  ? R.string.toast_tab_not_project
                  : R.string.toast_no_project));
      return;
    }
    projectField.setText(detected);
    projectField.setSelection(detected.length());
    toast(text(R.string.toast_project, detected));
  }

  private String project() {
    return projectField.getText().toString().trim();
  }

  /**
   * The project of the tab that is open right now, asked for and answered on the spot.
   *
   * <p>Nothing is remembered here on purpose. A remembered path is a path the user never chose for
   * this tab: they open a second project, the panel still builds the first one, and the field lies
   * about it. The host knows which file is in front of the user, so every tab that opens is a fresh
   * answer and the field shows exactly that.
   *
   * @return the folder that owns a {@code gradlew}, or an empty string when the open tab is not in
   *     one - the scripts then look for the project themselves.
   */
  private String tabProject() {
    EditorHost host = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host == null) {
      return "";
    }
    File open = host.getOpenFile();
    if (open != null) {
      File found = gradleProject(open.isDirectory() ? open : open.getParentFile(), 0);
      if (found != null) {
        return found.getAbsolutePath();
      }
    }
    File root = host.getProjectRoot();
    if (root != null && new File(root, "gradlew").isFile()) {
      return root.getAbsolutePath();
    }
    return "";
  }

  /**
   * Walks up from a file to the folder that holds the wrapper.
   *
   * <p>The walk has to be deep: a file in {@code app/src/main/java/a/b/c} sits eight folders below
   * the project root, and a limit that is too small lands on no answer at all - which is worse than
   * a wrong one, because the field then falls back to whatever project it finds first on the
   * device. {@value #UP_WALK} parents is far more than any real layout and still bounded, so the
   * walk always ends at the storage root.
   */
  private static File gradleProject(File directory, int depth) {
    if (directory == null || !directory.isDirectory() || depth > UP_WALK) {
      return null;
    }
    if (new File(directory, "gradlew").isFile()) {
      return directory;
    }
    return gradleProject(directory.getParentFile(), depth + 1);
  }

  /** Puts the live answer in the field, so what is shown is what the next build will use. */
  void syncProject() {
    String live = tabProject();
    if (live.isEmpty() || live.equals(project())) {
      return;
    }
    projectField.setText(live);
    projectField.setSelection(live.length());
  }

  /**
   * The host fires this for every tab that opens or closes, on whatever thread it likes: the field
   * is a view, so the update is posted to the thread that owns it.
   */
  void onTabChanged() {
    main.post(this::syncProject);
  }

  /**
   * What the detect button answers.
   *
   * <p>The open tab decides. Only when there is no tab at all does the panel go looking, because a
   * blind search of the storage finds the most recently touched project on the device, which has
   * nothing to do with the file the user is looking at - the field would then name a project the
   * user never opened.
   */
  private String guessProject() {
    String live = tabProject();
    if (!live.isEmpty()) {
      return live;
    }
    EditorHost host = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host != null && host.getOpenFile() != null) {
      return null;
    }
    File best = null;
    for (File directory : projectRoots()) {
      File found = findGradlew(directory, 0);
      if (found != null && (best == null || found.lastModified() > best.lastModified())) {
        best = found;
      }
    }
    return best == null ? null : best.getAbsolutePath();
  }

  private static List<File> projectRoots() {
    List<File> roots = new ArrayList<>();
    roots.add(new File("/storage/emulated/0/AndroidIDEProjects"));
    roots.add(new File("/storage/emulated/0"));
    return roots;
  }

  private static File findGradlew(File directory, int depth) {
    if (directory == null || !directory.isDirectory() || depth > 4) {
      return null;
    }
    if (new File(directory, "gradlew").isFile()) {
      return directory;
    }
    File[] children = directory.listFiles(File::isDirectory);
    if (children == null) {
      return null;
    }
    Arrays.sort(children);
    for (File child : children) {
      if (child.getName().startsWith(".")) {
        continue;
      }
      File found = findGradlew(child, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  // ── view helpers ───────────────────────────────────────────────────────────────

  private String text(int id) {
    return context.getString(id);
  }

  private String text(int id, Object... arguments) {
    return arguments.length == 0 ? context.getString(id) : context.getString(id, arguments);
  }

  private void set(int index, String value, int color) {
    TextView view = values[index];
    if (view == null) {
      return;
    }
    view.setText(value);
    view.setTextColor(color);
  }

  private String optionalLabel() {
    List<String> parts = new ArrayList<>();
    if (!env.ndk().isEmpty()) {
      parts.add(text(R.string.status_optional_item, "NDK", join(env.ndk())));
    }
    if (!env.cmake().isEmpty()) {
      parts.add(text(R.string.status_optional_item, "CMake", join(env.cmake())));
    }
    return parts.isEmpty() ? text(R.string.status_optional_none) : join(parts);
  }

  private static String size(File file) {
    long bytes = file.length();
    if (bytes >= 1024L * 1024L) {
      return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
    if (bytes >= 1024L) {
      return String.format(Locale.getDefault(), "%d KB", bytes / 1024L);
    }
    return bytes + " B";
  }

  private static String firstLine(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String line = text.split("\n", 2)[0].trim();
    return line.length() > 120 ? line.substring(0, 120) + "…" : line;
  }

  private static String join(List<String> values) {
    return String.join("، ", values);
  }

  private LinearLayout row(
      Context context, int text, int titleColor, int index, String title, String value) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView titleView = label(context, title, 12, titleColor, false);
    row.addView(
        titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));

    TextView valueView = label(context, value, 12, text, false);
    valueView.setGravity(Gravity.END);
    valueView.setTextIsSelectable(false);
    values[index] = valueView;
    row.addView(
        valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
    return row;
  }

  private TextView section(Context context, String text, int color) {
    TextView view = label(context, text, 12, color, true);
    view.setPadding(dp(context, 2), 0, 0, dp(context, 8));
    return view;
  }

  private LinearLayout card(Context context, int color, int stroke) {
    LinearLayout card = column();
    int pad = dp(context, 16);
    card.setPadding(pad, pad, pad, pad);
    card.setBackground(roundRect(color, stroke, 16));
    return card;
  }

  private TextView button(
      Context context,
      String text,
      float size,
      int textColor,
      int background,
      int stroke,
      int ripple,
      View.OnClickListener listener) {
    TextView view = label(context, text, size, textColor, true);
    view.setGravity(Gravity.CENTER);
    view.setClickable(true);
    view.setFocusable(true);
    view.setPadding(dp(context, 12), dp(context, 15), dp(context, 12), dp(context, 15));
    view.setBackground(pressable(background, stroke, ripple, 13));
    view.setOnClickListener(listener);
    return view;
  }

  private LinearLayout buttonRow(
      Context context,
      int background,
      int stroke,
      int ripple,
      int text,
      String left,
      View.OnClickListener leftListener,
      String right,
      View.OnClickListener rightListener) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.addView(
        button(context, left, 13, text, background, stroke, ripple, leftListener),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(space(context, 10));
    row.addView(
        button(context, right, 13, text, background, stroke, ripple, rightListener),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private TextView label(Context context, String text, float size, int color, boolean bold) {
    TextView view = new TextView(context);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(color);
    if (bold) {
      view.setTypeface(view.getTypeface(), Typeface.BOLD);
    }
    return view;
  }

  private static View space(Context context, int width) {
    View view = new View(context);
    view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, width), 1));
    return view;
  }

  private static LinearLayout.LayoutParams gap(Context context, int height) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(context, height);
    return params;
  }

  /** The same as {@link #gap}, but inside a vertical stack that already has a parent height. */
  private static LinearLayout.LayoutParams rowGap(Context context, int height) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(context, height);
    return params;
  }

  private GradientDrawable roundRect(int color, int stroke, int radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.RECTANGLE);
    drawable.setColor(color);
    drawable.setCornerRadius(dp(context, radiusDp));
    if (stroke != 0) {
      drawable.setStroke(Math.max(1, dp(context, 1)), stroke);
    }
    return drawable;
  }

  /** A rounded fill that answers the touch with a ripple, so a tap looks like a real control. */
  private Drawable pressable(int color, int stroke, int ripple, int radiusDp) {
    return new RippleDrawable(
        ColorStateList.valueOf(ripple), roundRect(color, stroke, radiusDp),
        roundRect(Color.WHITE, 0, radiusDp));
  }

  /** Keeps the colour, replaces its alpha: the palette's one knob for translucency. */
  private static int withAlpha(int color, int alpha) {
    return (color & 0x00FFFFFF) | (alpha << 24);
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
