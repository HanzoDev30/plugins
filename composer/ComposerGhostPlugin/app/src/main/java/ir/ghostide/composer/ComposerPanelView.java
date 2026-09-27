package ir.ghostide.composer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileManagerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The panel body: search Packagist, then require / remove / update, all inside the proot Debian.
 *
 * <p>Every view is created from the {@link android.view.ContextThemeWrapper} built by {@link
 * ThemeContext}, so the panel picks up the IDE theme instead of raw platform defaults, and every
 * colour is read from a theme attribute rather than hard-coded.
 */
final class ComposerPanelView {

  private static final String COMPOSER = "composer";
  private static final int MAX_LOG_LINES = 400;

  private static final String TITLE = "Composer";

  private static final int SUBTITLE = R.string.subtitle;
  private static final int READY = R.string.status_ready;
  private static final int BUSY = R.string.status_busy;
  private static final int OK = R.string.status_ok;
  private static final int FAILED = R.string.status_failed;
  private static final int NO_ROOTFS = R.string.status_no_rootfs;
  private static final int NO_COMPOSER = R.string.status_no_composer;
  private static final int NO_PROJECT = R.string.status_no_project;
  private static final int SEARCHING = R.string.status_searching;
  private static final int SEARCH_HINT = R.string.hint_search;
  private static final int COMMAND_HINT = R.string.hint_command;
  private static final int SEARCH_LABEL = R.string.action_search;
  private static final int INSTALL_LABEL = R.string.action_install;
  private static final int RUN_LABEL = R.string.action_run;
  private static final int TERMINAL_LABEL = R.string.action_terminal;
  private static final int PICK_LABEL = R.string.action_pick;
  private static final int SEARCH_SECTION = R.string.section_search;
  private static final int COMMAND_SECTION = R.string.section_command;
  private static final int CATALOG_SECTION = R.string.section_catalog;
  private static final int OUTPUT_SECTION = R.string.section_output;
  private static final int ACTIONS_SECTION = R.string.section_actions;
  private static final int CLEAR_LABEL = R.string.action_clear;
  private static final int SHOW_LABEL = R.string.action_show;
  private static final int INSTALL_ALL_LABEL = R.string.action_install_all;
  private static final int UPDATE_LABEL = R.string.action_update;
  private static final int VALIDATE_LABEL = R.string.action_validate;
  private static final int DUMP_LABEL = R.string.action_dump;
  private static final String SHOW_COMMAND = "composer show --direct 2>/dev/null || composer show";
  private static final String VALIDATE_COMMAND = "composer validate --no-check-publish";

  private static final String GUARD =
      "if ! command -v php >/dev/null 2>&1; then "
          + "apt update && apt install -y php-cli php-xml php-mbstring php-curl php-zip unzip git; fi; "
          + "if ! command -v composer >/dev/null 2>&1; then "
          + "{ apt update && apt install -y composer; } "
          + "|| { php -r \"copy('https://getcomposer.org/installer','composer-setup.php');\" "
          + "&& php composer-setup.php --install-dir=/usr/local/bin --filename=composer "
          + "&& rm -f composer-setup.php; } "
          + "|| { curl -sS https://getcomposer.org/installer | php -- "
          + "--install-dir=/usr/local/bin --filename=composer; }; fi; ";

  private static final int C_INSTALL = 0xFF34D399;
  private static final int C_SEARCH = 0xFF60A5FA;
  private static final int C_DIRECT = 0xFFA78BFA;
  private static final int C_TERMINAL = 0xFFFBBF24;
  private static final int C_CLEAR = 0xFFFB7185;
  private static final int C_LIST = 0xFF22D3EE;
  private static final int C_CATALOG = 0xFF2DD4BF;
  private static final int C_ON_ACCENT = 0xFF0B1220;
  private static final int C_TEXT = 0xFFF1F5F9;
  private static final int C_HINT = 0xFF94A3B8;
  private static final int C_FIELD = 0xFFFBBF24;

  private final PluginContext plugin;
  private Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean(false);
  private final ArrayDeque<String> logLines = new ArrayDeque<>();

  private final int accentColor;
  private final int primaryColor;
  private final int mutedColor;

  private final View root;
  private final EditText searchField;
  private final EditText commandField;
  private final LinearLayout resultCard;
  private final LinearLayout catalogCard;
  private final TextView statusLabel;
  private final TextView projectLabel;
  private final TextView logView;
  private final Button pickButton;
  private final Button clearButton;
  private final Button showButton;
  private final Button runButton;
  private final Button terminalButton;
  private final Button searchButton;
  private final Button searchInstallButton;

  private String projectDir = "";
  // private Context context;
  private PackagistClient.PackagistPackage pending;

  ComposerPanelView(Context pluginContext, PluginContext plugin) {
    this.plugin = plugin;
    this.context = ThemeContext.themed(pluginContext);
    this.accentColor = C_INSTALL;
    this.primaryColor = C_TEXT;
    this.mutedColor = C_HINT;

    LinearLayout content = column();
    content.setPadding(dp(18), dp(14), dp(18), dp(20));

    LinearLayout header = row();
    header.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    titles.addView(label(TITLE, 20, true, C_INSTALL));
    titles.addView(label(str(SUBTITLE), 12, false, C_HINT), gap(2));
    header.addView(
        titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    this.statusLabel = label("", 12, true, accentColor);
    header.addView(statusLabel);
    content.addView(header, gap(14));

    this.projectLabel = label(str(NO_PROJECT), 12, false, C_HINT);
    this.projectLabel.setSingleLine(true);
    LinearLayout projectRow = row();
    projectRow.setGravity(Gravity.CENTER_VERTICAL);
    projectRow.addView(
        this.projectLabel,
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    this.pickButton = button(str(PICK_LABEL), C_CATALOG, v -> askForProject());
    projectRow.addView(this.pickButton);
    content.addView(projectRow, gap(6));

    content.addView(label(str(SEARCH_SECTION), 14, true, C_SEARCH), gap(16));
    this.searchField = field(str(SEARCH_HINT));
    content.addView(this.searchField, gap(8));
    this.searchButton = button(str(SEARCH_LABEL), C_SEARCH, v -> search());
    this.searchInstallButton =
        button(
            str(INSTALL_LABEL), C_INSTALL, v -> installTyped(searchField.getText().toString().trim()));
    content.addView(buttonRow(this.searchButton, this.searchInstallButton), gap(14));

    this.resultCard = column();
    this.resultCard.setVisibility(View.GONE);
    content.addView(this.resultCard, gap(10));

    content.addView(label(str(COMMAND_SECTION), 14, true, C_DIRECT), gap(16));
    this.commandField = field(str(COMMAND_HINT));
    content.addView(this.commandField, gap(8));
    this.runButton =
        button(str(RUN_LABEL), C_DIRECT, v -> installTyped(commandField.getText().toString().trim()));
    this.terminalButton =
        button(
            str(TERMINAL_LABEL),
            C_TERMINAL,
            v -> openInTerminal(commandField.getText().toString().trim()));
    content.addView(buttonRow(this.runButton, this.terminalButton), gap(14));

    content.addView(label(str(ACTIONS_SECTION), 14, true, C_LIST), gap(16));
    content.addView(
        buttonRow(
            button(
                str(INSTALL_ALL_LABEL),
                C_INSTALL,
                v -> projectCommand("composer install", str(INSTALL_ALL_LABEL))),
            button(
                str(UPDATE_LABEL), C_SEARCH, v -> projectCommand("composer update", str(UPDATE_LABEL)))),
        gap(8));
    content.addView(
        buttonRow(
            button(
                str(VALIDATE_LABEL),
                C_CATALOG,
                v -> projectCommand(VALIDATE_COMMAND, str(VALIDATE_LABEL))),
            button(
                str(DUMP_LABEL),
                C_LIST,
                v -> projectCommand("composer dump-autoload", str(DUMP_LABEL)))),
        gap(14));

    content.addView(label(str(CATALOG_SECTION), 14, true, C_CATALOG), gap(16));
    this.catalogCard = column();
    content.addView(this.catalogCard, gap(14));

    content.addView(label(str(OUTPUT_SECTION), 14, true, C_LIST), gap(16));
    this.logView = new TextView(context);
    this.logView.setTextSize(11);
    this.logView.setTypeface(Typeface.MONOSPACE);
    this.logView.setTextIsSelectable(true);
    this.logView.setMinLines(6);
    this.logView.setTextColor(primaryColor);
    content.addView(this.logView, gap(8));
    this.showButton =
        button(str(SHOW_LABEL), C_LIST, v -> projectCommand(SHOW_COMMAND, str(SHOW_LABEL)));
    this.clearButton = button(str(CLEAR_LABEL), C_CLEAR, v -> clearLog());
    content.addView(buttonRow(this.showButton, this.clearButton));

    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.addView(
        content,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    this.root = scroll;

    this.searchField.setOnEditorActionListener(
        (v, actionId, event) -> {
          search();
          return true;
        });

    buildCatalog();
    appendLog(str(R.string.log_intro));
    refreshEnvironment();
  }

  private String str(int resId) {
    return context.getString(resId);
  }

  private String str(int resId, Object... args) {
    return context.getString(resId, args);
  }

  View getRoot() {
    return root;
  }

  private void refreshEnvironment() {
    projectDir = detectProject();
    worker.execute(
        () -> {
          boolean rootfs = ProotShell.isInstalled(context);
          boolean php = rootfs && ProotShell.hasBinary(context, "/usr/bin/php");
          boolean composer = rootfs && ProotShell.hasBinary(context, "/usr/bin/composer");
          String found = projectDir;
          main.post(
              () -> {
                projectLabel.setText(
                    found.isEmpty() ? str(NO_PROJECT) : str(R.string.label_project, found));
                if (!rootfs) {
                  setStatus(NO_ROOTFS, primaryColor);
                } else if (!php || !composer) {
                  setStatus(NO_COMPOSER, accentColor);
                } else {
                  setStatus(READY, accentColor);
                }
              });
        });
  }

  private String detectProject() {
    File start = null;
    try {
      EditorHost editor = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
      if (editor != null) {
        start = editor.getProjectRoot();
      }
    } catch (RuntimeException ignored) {
    }
    if (start == null) {
      try {
        FileManagerHost fileManager = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
        if (fileManager != null) {
          start = fileManager.getRootDirectory();
        }
      } catch (RuntimeException ignored) {
      }
    }
    return findComposerRoot(start);
  }

  private static String findComposerRoot(File start) {
    File dir = start;
    for (int depth = 0; dir != null && depth < 24; depth++) {
      if (new File(dir, "composer.json").isFile()) {
        return dir.getAbsolutePath();
      }
      dir = dir.getParentFile();
    }
    return "";
  }

  private void askForProject() {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback == null) {
      toast(str(NO_PROJECT));
      return;
    }
    feedback.promptInput(
        str(PICK_LABEL),
        projectDir,
        path -> {
          if (path == null || path.trim().isEmpty()) {
            return;
          }
          File dir = new File(path.trim());
          if (!dir.isDirectory()) {
            toast(str(NO_PROJECT));
            return;
          }
          projectDir = dir.getAbsolutePath();
          refreshEnvironment();
        });
  }

  private void setStatus(int textRes, int color) {
    statusLabel.setText(str(textRes));
    statusLabel.setTextColor(color);
  }

  private void search() {
    String query = searchField.getText().toString().trim();
    if (query.isEmpty()) {
      resultCard.setVisibility(View.GONE);
      toast(str(R.string.hint_type_package));
      return;
    }
    resultCard.setVisibility(View.VISIBLE);
    resultCard.removeAllViews();
    resultCard.addView(label(str(SEARCHING), 13, false, accentColor));
    worker.execute(
        () -> {
          PackagistClient.Result result = PackagistClient.lookup(query);
          main.post(() -> showResult(query, result));
        });
  }

  private void showResult(String query, PackagistClient.Result result) {
    resultCard.removeAllViews();
    if (!result.isFound()) {
      String message =
          result.error.startsWith("no package")
              ? str(R.string.error_not_found, PackagistClient.baseName(query))
              : result.error;
      resultCard.addView(label(message, 13, true, primaryColor));
      return;
    }

    PackagistClient.PackagistPackage pkg = result.pkg;
    this.pending = pkg;
    String requirement = PackagistClient.requirement(query, pkg.name);

    resultCard.addView(label(pkg.name, 16, true, primaryColor));
    if (!pkg.version.isEmpty()) {
      resultCard.addView(label(str(R.string.label_version, pkg.version), 12, false, accentColor), gap(2));
    }
    if (!pkg.downloads.isEmpty()) {
      resultCard.addView(label(str(R.string.label_downloads, pkg.downloads), 12, false, mutedColor), gap(2));
    }
    if (!pkg.license.isEmpty()) {
      resultCard.addView(label(str(R.string.label_license, pkg.license), 12, false, mutedColor), gap(2));
    }
    if (!pkg.requiresPhp.isEmpty()) {
      resultCard.addView(label(str(R.string.label_requires, pkg.requiresPhp), 12, false, mutedColor), gap(2));
    }
    if (!pkg.summary.isEmpty()) {
      resultCard.addView(label(pkg.summary, 13, false, mutedColor), gap(6));
    }
    resultCard.addView(
        button(
            str(R.string.action_install_requirement, requirement),
            C_INSTALL,
            v -> {
              if (pending != null) {
                require(pkg.name, requirement);
              }
            }),
        gap(4));
  }

  private void installTyped(String rawCommand) {
    if (rawCommand.isEmpty()) {
      toast(str(R.string.hint_type_command));
      return;
    }
    if (rawCommand.startsWith(COMPOSER + " ") || rawCommand.equals(COMPOSER)) {
      run(projectDir, rawCommand, str(RUN_LABEL), rawCommand);
      return;
    }
    String sub =
        rawCommand.startsWith("require ")
            ? rawCommand.substring("require ".length()).trim()
            : rawCommand;
    if (sub.isEmpty()) {
      toast(str(R.string.hint_type_command));
      return;
    }
    require(PackagistClient.baseName(sub), sub);
  }

  private void require(String name, String requirement) {
    String command = COMPOSER + " require " + requirement + " --no-interaction";
    run(projectDir, command, str(R.string.action_install_requirement, name), command);
  }

  private void projectCommand(String command, String title) {
    if (projectDir.isEmpty()) {
      askForProject();
      return;
    }
    run(projectDir, command, title, command);
  }

  private void openInTerminal(String command) {
    if (command.isEmpty()) {
      toast(str(R.string.hint_type_command));
      return;
    }
    if (!ProotShell.isInstalled(context)) {
      toast(str(NO_ROOTFS));
      return;
    }
    String full = command.startsWith(COMPOSER) ? command : COMPOSER + " " + command;
    try {
      Intent intent = new Intent("ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity");
      String cd = projectDir.isEmpty() ? "" : "cd " + shellQuote(projectDir) + " && ";
      intent.putExtra("command", cd + GUARD + full);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    } catch (RuntimeException e) {
      plugin.getLogger().warn("Terminal intent failed", e);
      toast(str(TERMINAL_LABEL));
    }
  }

  private void run(String workingDir, String command, String title, String echo) {
    if (!busy.compareAndSet(false, true)) {
      return;
    }
    setButtonsEnabled(false);
    setStatus(BUSY, accentColor);
    appendLog("$ " + echo);

    String guard = workingDir.isEmpty() ? "" : GUARD;
    worker.execute(
        () -> {
          try {
            ProotShell.Result result =
                ProotShell.run(
                    context, workingDir, guard + command, line -> main.post(() -> appendLog(line)));
            main.post(
                () -> {
                  appendLog(
                      (result.isSuccess() ? "✔ " : "✘ ")
                          + title
                          + (result.isSuccess() ? "" : " (exit " + result.exitCode + ")"));
                  finish(result.isSuccess());
                });
          } catch (Exception e) {
            String message = e.getMessage();
            main.post(
                () -> {
                  appendLog(
                      str(
                          R.string.log_error,
                          message == null || message.isEmpty()
                              ? e.getClass().getSimpleName()
                              : message));
                  finish(false);
                });
          }
        });
  }

  private void finish(boolean success) {
    busy.set(false);
    setButtonsEnabled(true);
    setStatus(success ? OK : FAILED, success ? accentColor : primaryColor);
    refreshEnvironment();
  }

  private void buildCatalog() {
    catalogCard.removeAllViews();
    for (PackageCatalog.Group group : PackageCatalog.groups()) {
      catalogCard.addView(label(group.title, 13, true, accentColor), gap(10));
      for (PackageCatalog.Entry entry : group.entries) {
        catalogCard.addView(catalogRow(entry), gap(4));
      }
    }
  }

  private void requireEntry(PackageCatalog.Entry entry) {
    searchField.setText(entry.name);
    require(entry.name, entry.name);
  }

  private View catalogRow(PackageCatalog.Entry entry) {
    LinearLayout row = row();
    row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    titles.addView(label(entry.name, 13, true, primaryColor));
    titles.addView(label(entry.summary, 11, false, mutedColor));
    row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(button(str(INSTALL_LABEL), C_INSTALL, v -> requireEntry(entry)));
    return row;
  }

  private void appendLog(String line) {
    logLines.addLast(line);
    while (logLines.size() > MAX_LOG_LINES) {
      logLines.removeFirst();
    }
    renderLog();
  }

  private void renderLog() {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    for (String line : logLines) {
      int start = builder.length();
      builder.append(line);
      builder.setSpan(
          new ForegroundColorSpan(logColor(line)),
          start,
          builder.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      builder.append('\n');
    }
    logView.setText(builder);
  }

  private int logColor(String line) {
    if (line.startsWith("✔") || line.startsWith("$")) {
      return accentColor;
    }
    if (line.startsWith("✘") || line.startsWith(str(R.string.log_error, "").trim())) {
      return mutedColor;
    }
    return primaryColor;
  }

  private void clearLog() {
    logLines.clear();
    logView.setText("");
  }

  private void setButtonsEnabled(boolean enabled) {
    for (Button button :
        new Button[] {
          clearButton,
          showButton,
          runButton,
          terminalButton,
          searchButton,
          searchInstallButton,
          pickButton
        }) {
      button.setEnabled(enabled);
    }
  }

  private void toast(String message) {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message, false);
    }
  }

  private EditText field(String hint) {
    EditText field = new EditText(context);
    field.setHint(hint);
    field.setTextSize(14);
    field.setSingleLine(true);
    field.setHintTextColor(C_HINT);
    field.setTextColor(C_FIELD);
    field.setInputType(InputType.TYPE_CLASS_TEXT);
    return field;
  }

  private TextView label(String text, float size, boolean bold, int color) {
    TextView view = new TextView(context);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(color);
    if (bold) {
      view.setTypeface(view.getTypeface(), Typeface.BOLD);
    }
    return view;
  }

  private Button button(String text, int accent, View.OnClickListener listener) {
    Button button = new Button(context);
    button.setText(text);
    button.setAllCaps(false);
    button.setTextSize(13);
    button.setTextColor(C_ON_ACCENT);
    button.setTypeface(button.getTypeface(), Typeface.BOLD);

    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setColor(accent);
    bg.setCornerRadius(dp(14));
    button.setBackground(bg);

    int padH = dp(14);
    int padV = dp(8);
    button.setPadding(padH, padV, padH, padV);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setMinimumWidth(0);
    button.setOnClickListener(listener);
    return button;
  }

  private LinearLayout buttonRow(View left, View right) {
    LinearLayout holder = row();
    holder.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    holder.addView(space(dp(10)));
    holder.addView(
        right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return holder;
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private LinearLayout row() {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.HORIZONTAL);
    return layout;
  }

  private View space(int width) {
    View view = new View(context);
    view.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
    return view;
  }

  private LinearLayout.LayoutParams gap(int height) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(height);
    return params;
  }

  private int themeColor(int attr, int fallback) {
    TypedValue value = new TypedValue();
    if (context.getTheme().resolveAttribute(attr, value, true)) {
      if (value.resourceId != 0) {
        try {
          return context.getResources().getColor(value.resourceId, context.getTheme());
        } catch (RuntimeException ignored) {
        }
      }
      if (value.data != 0) {
        return value.data;
      }
    }
    return fallback;
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}
