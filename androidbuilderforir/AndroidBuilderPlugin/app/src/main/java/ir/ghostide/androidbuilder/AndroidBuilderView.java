package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 */
final class AndroidBuilderView {

  private static final String KEY_PROJECT = "project";

  private static final int OK = 0xFF2E7D32;
  private static final int IDLE = 0xFF78909C;
  private static final int ACCENT = 0xFF2E7D32;
  private static final int PANEL_STROKE = 0x1FFFFFFF;

  private final Context context;
  private final PluginContext plugin;
  private final SdkEnvironment env;
  private final PluginScripts scripts;
  private final TerminalLauncher launcher;
  private final View root;

  private final EditText projectField;
  private final TextView[] values = new TextView[7];

  AndroidBuilderView(Context context, PluginContext plugin, SharedPreferences preferences) {
    this.context = context;
    this.plugin = plugin;
    this.env = new SdkEnvironment(plugin);
    this.scripts = new PluginScripts(plugin);
    this.launcher = new TerminalLauncher(plugin, context);

    int background = themeColor(context, android.R.attr.colorBackground, 0xFF15181F);
    int surface = blend(background, Color.WHITE, 0.07f);
    int text = themeColor(context, android.R.attr.textColorPrimary, 0xFFECEFF4);
    int dim = blend(text, background, 0.45f);

    this.projectField = new EditText(context);
    projectField.setText(preferences.getString(KEY_PROJECT, ""));
    projectField.setHint("خالی = تشخیص خودکار پروژه");
    projectField.setHintTextColor(blend(dim, background, 0.35f));
    projectField.setTextColor(text);
    projectField.setTextSize(13);
    projectField.setSingleLine(true);
    projectField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    projectField.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
    projectField.setBackground(
        roundRect(blend(surface, Color.TRANSPARENT, 0.05f), PANEL_STROKE, 10));
    projectField.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            preferences.edit().putString(KEY_PROJECT, s.toString().trim()).apply();
          }
        });

    LinearLayout content = column();
    content.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 20));

    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    titles.addView(label(context, "AndroidBuilder for ir", 19, text, true));
    titles.addView(
        label(
            context, "ساخت APK با ./gradlew — دانلود‌ها از آینهٔ maven.myket.ir", 12, dim, false));
    header.addView(
        titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(
        button(
            context, "به‌روزرسانی", 12, text, blend(surface, Color.WHITE, 0.10f), v -> refresh()),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    content.addView(header, gap(context, 14));

    content.addView(section(context, "وضعیت محیط", dim));
    LinearLayout statusCard = card(context, surface);
    statusCard.addView(row(context, text, dim, 0, "پروژهٔ ترمینال (proot)", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 1, "JDK 17", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 2, "Android SDK", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 3, "build-tools", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 4, "platforms", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 5, "آینهٔ Gradle", "در حال بررسی…"));
    statusCard.addView(row(context, text, dim, 6, "NDK / CMake (اختیاری)", "در حال بررسی…"));
    content.addView(statusCard, gap(context, 14));

    content.addView(section(context, "پروژه", dim));
    LinearLayout projectCard = card(context, surface);
    projectCard.addView(label(context, "مسیر پروژه‌ای که gradlew دارد", 12, dim, false));
    LinearLayout projectRow = new LinearLayout(context);
    projectRow.setOrientation(LinearLayout.HORIZONTAL);
    projectRow.setGravity(Gravity.CENTER_VERTICAL);
    projectRow.addView(
        projectField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    projectRow.addView(space(context, 10));
    projectRow.addView(
        button(
            context, "تشخیص", 13, text, blend(surface, Color.WHITE, 0.10f), v -> detectProject()),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    projectCard.addView(projectRow, gap(context, 6));
    projectCard.addView(
        label(
            context,
            "خالی بگذاری، اسکریپت خودش پروژه‌ای که ./gradlew دارد را پیدا می‌کند.",
            11,
            dim,
            false));
    content.addView(projectCard, gap(context, 14));

    content.addView(section(context, "نصب و راه‌اندازی", dim));
    LinearLayout setup = column();
    setup.addView(
        buttonRow(
            context,
            surface,
            text,
            "نصب JDK 17",
            v -> run(PluginScripts.INSTALL_JDK),
            "نصب SDK از آینه",
            v -> run(PluginScripts.INSTALL_SDK)));
    setup.addView(
        buttonRow(
            context,
            surface,
            text,
            "نصب NDK (اختیاری)",
            v -> run(PluginScripts.INSTALL_SDK, "ndk"),
            "نصب CMake (اختیاری)",
            v -> run(PluginScripts.INSTALL_SDK, "cmake")));
    setup.addView(
        buttonRow(
            context,
            surface,
            text,
            "آینهٔ Gradle",
            v -> run(PluginScripts.CONFIGURE_GRADLE),
            "ابزارهای ARM64",
            v -> run(PluginScripts.PATCH_BUILD_TOOLS)));
    setup.addView(
        button(
            context,
            env.hasCommandLineTools() ? "sdkmanager نصب است" : "نصب sdkmanager (اختیاری، ۱۳۰ مگ)",
            12,
            text,
            blend(surface, Color.WHITE, 0.06f),
            v -> run(PluginScripts.INSTALL_SDK, "tools")));
    content.addView(setup, gap(context, 16));

    content.addView(section(context, "ساخت", dim));
    LinearLayout build = new LinearLayout(context);
    build.setOrientation(LinearLayout.HORIZONTAL);
    build.addView(
        button(context, "ساخت Debug APK", 14, Color.WHITE, ACCENT, v -> build("assembleDebug")),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    build.addView(space(context, 10));
    build.addView(
        button(
            context,
            "ساخت Release APK",
            14,
            Color.WHITE,
            blend(ACCENT, Color.BLACK, 0.20f),
            v -> build("assembleRelease")),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    content.addView(build, gap(context, 8));
    content.addView(
        label(
            context,
            "هر دکمه ترمینال را باز می‌کند و کار را همان‌جا انجام می‌دهد.",
            11,
            dim,
            false));

    ScrollView scroll = new ScrollView(context);
    scroll.setBackgroundColor(background);
    scroll.setFillViewport(true);
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
    set(0, env.hasRootfs() ? "Debian آماده است" : "هنوز بوت نشده", env.hasRootfs());
    set(1, env.hasJdk() ? env.jdkLabel() : "نصب نیست", env.hasJdk());
    set(2, env.hasSdk() ? env.sdkPath() : "نصب نیست", env.hasSdk());
    set(3, env.buildTools().isEmpty() ? "نصب نیست" : join(env.buildTools()), env.hasBuildTools());
    set(
        4,
        env.platforms().isEmpty() ? "نصب نیست" : join(env.platforms()),
        !env.platforms().isEmpty());
    set(5, env.gradleMirrorReady() ? "init.gradle فعال" : "تنظیم نشده", env.gradleMirrorReady());
    String optional = optionalLabel();
    set(6, optional, !env.ndk().isEmpty() || !env.cmake().isEmpty());
  }

  // ── actions ────────────────────────────────────────────────────────────────────

  private void run(String asset, String... arguments) {
    String command = scripts.command(asset, arguments);
    if (command == null) {
      launcher.toast("اسکریپت در دسترس نیست: " + asset);
      return;
    }
    launcher.run(command);
  }

  private void build(String task) {
    List<String> missing = env.missingForBuild();
    if (missing.isEmpty()) {
      run(PluginScripts.BUILD_APK, task, project());
      return;
    }
    askToInstall(missing, task);
  }

  private void askToInstall(List<String> missing, String task) {
    StringBuilder message = new StringBuilder();
    for (String item : missing) {
      message.append("• ").append(item).append('\n');
    }
    message.append("\nهمین الان در ترمینال نصبش کنم و بعد بیلد بگیرم؟");

    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback == null) {
      runInstall(missing, task);
      return;
    }
    feedback.confirm(
        "محیط بیلد کامل نیست",
        message.toString().trim(),
        accepted -> {
          if (accepted) {
            runInstall(missing, task);
          }
        });
  }

  /** Chains the missing steps and the build into a single terminal session. */
  private void runInstall(List<String> missing, String task) {
    List<String> steps = new ArrayList<>();
    for (String item : missing) {
      if (item.contains("JDK")) {
        steps.add(scripts.command(PluginScripts.INSTALL_JDK));
      } else if (item.contains("SDK") || item.contains("build-tools")) {
        steps.add(scripts.command(PluginScripts.INSTALL_SDK));
      } else if (item.contains("آینه")) {
        steps.add(scripts.command(PluginScripts.CONFIGURE_GRADLE));
      }
    }
    if (env.hasBuildTools() && env.patchedBuildTools() == 0) {
      steps.add(scripts.command(PluginScripts.PATCH_BUILD_TOOLS));
    }
    String build = scripts.command(PluginScripts.BUILD_APK, task, project());
    if (build == null || hasEmptyStep(steps)) {
      launcher.toast("اسکریپت‌ها کامل آماده نشدند");
      return;
    }
    steps.add(build.trim());
    launcher.run(String.join(" && ", steps));
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

  private void detectProject() {
    String detected = guessProject();
    if (detected == null) {
      launcher.toast("پروژه‌ای با ./gradlew پیدا نشد");
      return;
    }
    projectField.setText(detected);
    projectField.setSelection(detected.length());
    launcher.toast("پروژه: " + detected);
  }

  private String project() {
    return projectField.getText().toString().trim();
  }

  private String guessProject() {
    EditorHost host = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
    if (host != null) {
      File root = host.getProjectRoot();
      if (root != null && new File(root, "gradlew").isFile()) {
        return root.getAbsolutePath();
      }
      File open = host.getOpenFile();
      if (open != null
          && open.getParentFile() != null
          && new File(open.getParentFile(), "gradlew").isFile()) {
        return open.getParentFile().getAbsolutePath();
      }
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

  private void set(int index, String value, boolean ready) {
    TextView view = values[index];
    if (view == null) {
      return;
    }
    view.setText(value);
    view.setTextColor(ready ? OK : IDLE);
  }

  private String optionalLabel() {
    List<String> parts = new ArrayList<>();
    if (!env.ndk().isEmpty()) {
      parts.add("NDK " + join(env.ndk()));
    }
    if (!env.cmake().isEmpty()) {
      parts.add("CMake " + join(env.cmake()));
    }
    return parts.isEmpty() ? "نصب نشده — برای اپ‌های Kotlin لازم نیست" : join(parts);
  }

  private static String join(List<String> values) {
    return String.join("، ", values);
  }

  private LinearLayout row(
      Context context, int text, int dim, int index, String title, String value) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);

    TextView titleView = label(context, title, 12, dim, false);
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

  private TextView section(Context context, String text, int dim) {
    TextView view = label(context, text, 12, dim, true);
    view.setPadding(0, 0, 0, dp(context, 6));
    return view;
  }

  private LinearLayout card(Context context, int color) {
    LinearLayout card = column();
    int pad = dp(context, 14);
    card.setPadding(pad, pad, pad, pad);
    card.setBackground(roundRect(color, PANEL_STROKE, 14));
    return card;
  }

  private TextView button(
      Context context,
      String text,
      float size,
      int textColor,
      int background,
      View.OnClickListener listener) {
    TextView view = label(context, text, size, textColor, true);
    view.setGravity(Gravity.CENTER);
    view.setClickable(true);
    view.setFocusable(true);
    view.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 14));
    view.setBackground(roundRect(background, PANEL_STROKE, 12));
    view.setOnClickListener(listener);
    return view;
  }

  private LinearLayout buttonRow(
      Context context,
      int surface,
      int text,
      String left,
      View.OnClickListener leftListener,
      String right,
      View.OnClickListener rightListener) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    int background = blend(surface, Color.WHITE, 0.10f);
    row.addView(
        button(context, left, 13, text, background, leftListener),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(space(context, 10));
    row.addView(
        button(context, right, 13, text, background, rightListener),
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

  private static GradientDrawable roundRect(int color, int stroke, int radiusDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.RECTANGLE);
    drawable.setColor(color);
    drawable.setCornerRadius(radiusDp * 3f);
    if (stroke != 0) {
      drawable.setStroke(1, stroke);
    }
    return drawable;
  }

  private static int themeColor(Context context, int attribute, int fallback) {
    try {
      TypedValue value = new TypedValue();
      if (context.getTheme().resolveAttribute(attribute, value, true)) {
        if (value.resourceId != 0) {
          return context.getResources().getColor(value.resourceId, context.getTheme());
        }
        return value.data;
      }
    } catch (Exception ignored) {
      // Falls through to the built in dark palette.
    }
    return fallback;
  }

  private static int blend(int base, int overlay, float amount) {
    int red = Math.round(Color.red(base) + (Color.red(overlay) - Color.red(base)) * amount);
    int green = Math.round(Color.green(base) + (Color.green(overlay) - Color.green(base)) * amount);
    int blue = Math.round(Color.blue(base) + (Color.blue(overlay) - Color.blue(base)) * amount);
    return Color.rgb(red, green, blue);
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
