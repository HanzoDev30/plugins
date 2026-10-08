package ir.hanzodev30.nodeui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

final class NodePanelView {

  private static final String NPM_GUARD =
      "if ! command -v npm >/dev/null 2>&1; then apt update && apt install nodejs npm -y; fi; ";

  private static final int MAX_LOG_LINES = 400;

  private final Context context;
  private final PluginContext plugin;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean(false);
  private final ArrayDeque<String> logLines = new ArrayDeque<>();

  private final View root;
  private final EditText searchField;
  private final TextView statusLabel;
  private final View resultCard;
  private final TextView resultName;
  private final TextView resultVersion;
  private final TextView resultDesc;
  private final Button searchButton;
  private final Button installButton;
  private final Button resultInstall;
  private final Button clearButton;
  private final CheckBox globalBox;
  private final CheckBox devBox;
  private final CheckBox exactBox;
  private final TextView logView;
  private final ScrollView logScroll;
  private final LibAdapter adapter;

  private NodeClient.Package pending;
  private File jsTarget;

  NodePanelView(Context hostContext, PluginContext plugin) {
    this.plugin = plugin;
    this.context = new ContextThemeWrapper(hostContext, R.style.NodeUiTheme);

    root = LayoutInflater.from(context).inflate(R.layout.nodeui_panel, null, false);

    statusLabel = root.findViewById(R.id.nodeui_status);
    searchField = root.findViewById(R.id.nodeui_search);
    searchButton = root.findViewById(R.id.nodeui_search_button);
    installButton = root.findViewById(R.id.nodeui_install_button);
    resultCard = root.findViewById(R.id.nodeui_result);
    resultName = root.findViewById(R.id.nodeui_result_name);
    resultVersion = root.findViewById(R.id.nodeui_result_version);
    resultDesc = root.findViewById(R.id.nodeui_result_desc);
    resultInstall = root.findViewById(R.id.nodeui_result_install);
    globalBox = root.findViewById(R.id.nodeui_check_global);
    devBox = root.findViewById(R.id.nodeui_check_dev);
    exactBox = root.findViewById(R.id.nodeui_check_exact);
    clearButton = root.findViewById(R.id.nodeui_clear);
    logView = root.findViewById(R.id.nodeui_log);
    logScroll = root.findViewById(R.id.nodeui_log_scroll);

    applyColors();

    RecyclerView list = root.findViewById(R.id.nodeui_list);
    list.setLayoutManager(new LinearLayoutManager(context));
    adapter =
        new LibAdapter(
            context, name -> install(name, getString(R.string.nodeui_title_install, name)));
    list.setAdapter(adapter);

    searchButton.setOnClickListener(v -> doSearch());
    installButton.setOnClickListener(v -> installTyped(searchField.getText().toString()));
    resultInstall.setOnClickListener(
        v -> {
          if (pending != null) {
            install(pending.name, getString(R.string.nodeui_title_install, pending.name));
          }
        });
    clearButton.setOnClickListener(v -> clearLog());

    globalBox.setOnCheckedChangeListener(
        (button, checked) -> {
          devBox.setEnabled(!checked);
          exactBox.setEnabled(!checked);
          if (checked) {
            devBox.setChecked(false);
            exactBox.setChecked(false);
          }
        });

    searchField.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            adapter.filter(s == null ? "" : s.toString());
            resultCard.setVisibility(View.GONE);
          }
        });

    applyLayoutDirection();
    appendLog(getString(R.string.nodeui_log_intro));
    refreshProject();
    refreshEnvironment();
  }

  View getRoot() {
    return root;
  }

  void onFileEvent() {
    main.post(this::refreshProject);
  }

  void shutdown() {
    worker.shutdownNow();
  }

  private void applyColors() {
    root.setBackgroundColor(Palette.surface());

    TextView title = root.findViewById(R.id.nodeui_title);
    TextView subtitle = root.findViewById(R.id.nodeui_subtitle);
    TextView catalogTitle = root.findViewById(R.id.nodeui_catalog_title);
    TextView outputTitle = root.findViewById(R.id.nodeui_output_title);

    title.setTextColor(Palette.primary());
    subtitle.setTextColor(Palette.onSurfaceVariant());
    catalogTitle.setTextColor(Palette.primary());
    outputTitle.setTextColor(Palette.primary());
    statusLabel.setTextColor(Palette.onSurfaceVariant());

    searchField.setTextColor(Palette.onSurface());
    searchField.setHintTextColor(Palette.onSurfaceVariant());

    accent(searchButton);
    accent(installButton);
    accent(resultInstall);
    clearButton.setTextColor(Palette.primary());

    resultName.setTextColor(Palette.primary());
    resultVersion.setTextColor(Palette.onSurfaceVariant());
    resultDesc.setTextColor(Palette.onSurface());

    tintCheckbox(globalBox);
    tintCheckbox(devBox);
    tintCheckbox(exactBox);

    logScroll.setBackgroundColor(Palette.surfaceContainer());
    logScroll.setPadding(dp(10), dp(6), dp(10), dp(6));
    logView.setTextColor(Palette.onSurface());
  }

  private void accent(Button button) {
    button.setBackgroundTintList(ColorStateList.valueOf(Palette.primary()));
    button.setTextColor(Palette.onPrimary());
  }

  private void tintCheckbox(CheckBox box) {
    box.setButtonTintList(ColorStateList.valueOf(Palette.primary()));
    box.setTextColor(Palette.onSurface());
  }

  private void applyLayoutDirection() {
    root.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
  }

  private void refreshProject() {
    EditorHost host = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
    File next = null;
    if (host != null) {
      File open = host.getOpenFile();
      File root = host.getProjectRoot();
      if (open != null) {
        next = findPackageJson(open);
      }
      if (next == null && root != null) {
        next = findPackageJson(root);
      }
      if (next == null
          && root != null
          && root.isDirectory()
          && isJsProject(open, root)) {
        next = root;
      }
    }

    if (next == null) {
      jsTarget = null;
      return;
    }
    if (!next.equals(jsTarget)) {
      jsTarget = next;
      appendLog(getString(R.string.nodeui_log_target, next.getAbsolutePath()));
    }
  }

  private static boolean isJsProject(File open, File root) {
    if (open != null && isJsFamily(open.getName())) {
      return true;
    }
    File[] files = root.listFiles();
    if (files == null) {
      return false;
    }
    for (File file : files) {
      if (file.isFile() && isJsFamily(file.getName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isJsFamily(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return false;
    }
    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
    switch (ext) {
      case "js":
      case "jsx":
      case "mjs":
      case "cjs":
      case "ts":
      case "tsx":
      case "mts":
      case "cts":
      case "json":
      case "vue":
      case "svelte":
      case "astro":
        return true;
      default:
        return false;
    }
  }

  private static File findPackageJson(File start) {
    File dir = start.isDirectory() ? start : start.getParentFile();
    for (int level = 0; dir != null && level < 20; level++) {
      if (new File(dir, "package.json").isFile()) {
        return dir;
      }
      dir = dir.getParentFile();
    }
    return null;
  }

  private void refreshEnvironment() {
    worker.execute(
        () -> {
          boolean rootfs = ProotShell.isInstalled(context);
          boolean node =
              rootfs
                  && (ProotShell.hasBinary(context, "/usr/bin/npm")
                      || ProotShell.hasBinary(context, "/usr/local/bin/npm"));
          main.post(
              () -> {
                if (!rootfs) {
                  setStatus(getString(R.string.nodeui_status_no_debian), Palette.error());
                } else if (!node) {
                  setStatus(getString(R.string.nodeui_status_no_node), Palette.warning());
                } else {
                  setStatus(getString(R.string.nodeui_status_ready), Palette.success());
                }
              });
        });
  }

  private void setStatus(String text, int color) {
    statusLabel.setText(text);
    statusLabel.setTextColor(color);
  }

  private void doSearch() {
    String query = searchField.getText().toString().trim();
    if (query.isEmpty()) {
      toast(getString(R.string.nodeui_toast_type_package_name));
      return;
    }

    resultCard.setVisibility(View.VISIBLE);
    resultName.setText(getString(R.string.nodeui_searching));
    resultName.setTextColor(Palette.primary());
    resultVersion.setText("");
    resultDesc.setText("");
    resultInstall.setVisibility(View.GONE);
    pending = null;

    worker.execute(
        () -> {
          NodeClient.Result result = NodeClient.lookup(context, query);
          main.post(() -> showResult(result));
        });
  }

  private void showResult(NodeClient.Result result) {
    if (!result.isFound()) {
      pending = null;
      resultName.setText(result.error);
      resultName.setTextColor(Palette.error());
      resultVersion.setText("");
      resultDesc.setText(getString(R.string.nodeui_search_fail_hint));
      resultDesc.setTextColor(Palette.onSurfaceVariant());
      resultInstall.setVisibility(View.GONE);
      return;
    }

    pending = result.pkg;
    resultName.setText(pending.name);
    resultName.setTextColor(Palette.primary());

    String version = getString(R.string.nodeui_label_version, pending.version);
    if (!pending.author.isEmpty()) {
      version = version + "  " + getString(R.string.nodeui_label_author, pending.author);
    }
    resultVersion.setText(version);
    resultVersion.setTextColor(Palette.onSurfaceVariant());

    resultDesc.setText(pending.summary);
    resultDesc.setTextColor(Palette.onSurface());
    resultInstall.setVisibility(View.VISIBLE);
  }

  private void installTyped(String raw) {
    String typed = raw == null ? "" : raw.trim();
    if (typed.isEmpty()) {
      toast(getString(R.string.nodeui_toast_type_package_name));
      return;
    }
    if (typed.startsWith("npm ")) {
      typed = typed.substring("npm ".length()).trim();
    }
    if (typed.startsWith("install ")) {
      typed = typed.substring("install ".length()).trim();
    }
    if (typed.isEmpty()) {
      toast(getString(R.string.nodeui_toast_empty_package_name));
      return;
    }

    String base = NodeClient.baseName(typed);
    install(typed, getString(R.string.nodeui_title_install, base.isEmpty() ? typed : base));
  }

  private void install(String spec, String title) {
    String clean = spec == null ? "" : spec.trim();
    if (clean.isEmpty()) {
      toast(getString(R.string.nodeui_toast_empty_package_name));
      return;
    }

    boolean global = globalBox.isChecked();
    String mode;
    if (global) {
      mode = "npm install -g";
    } else {
      String target = installTarget();
      if (target == null) {
        appendLog(getString(R.string.nodeui_log_no_project));
        toast(getString(R.string.nodeui_toast_no_project));
        return;
      }
      if (devBox.isChecked()) {
        mode = "npm install -D";
      } else {
        mode = "npm install";
      }
      if (exactBox.isChecked()) {
        mode = mode + " --save-exact";
      }
      String echo = mode + " " + clean;
      run(NPM_GUARD + localInstallCommand(mode + " " + clean, target), null, title, echo);
      return;
    }

    String echo = mode + " " + clean;
    run(NPM_GUARD + echo, null, title, echo);
  }

  private String localInstallCommand(String npmArgs, String projectPath) {
    String proj = projectPath.replace("'", "'\\''");
    StringBuilder sb = new StringBuilder();
    sb.append("rm -rf /root/.nodeui-stage && mkdir -p /root/.nodeui-stage && ");
    sb.append("PROJ='").append(proj).append("' && ");
    sb.append(
        "NAME=$(basename \"$PROJ\" | tr -c 'a-zA-Z0-9._-' '-' | cut -c1-40) && ");
    sb.append("NAME=${NAME:-project} && ");
    sb.append(
        "if [ -f \"$PROJ/package.json\" ]; then cp \"$PROJ/package.json\" /root/.nodeui-stage/; ");
    sb.append(
        "else printf '{\\n  \\\"name\\\": \\\"%s\\\",\\n  \\\"version\\\": \\\"1.0.0\\\"\\n}\\n' \"$NAME\" > /root/.nodeui-stage/package.json; fi && ");
    sb.append("cd /root/.nodeui-stage && ");
    sb.append(npmArgs);
    sb.append(" --no-audit --no-fund && ");
    sb.append("mkdir -p \"$PROJ/node_modules\" && ");
    sb.append("cp -rLf /root/.nodeui-stage/node_modules/. \"$PROJ/node_modules/\" && ");
    sb.append("cp -f /root/.nodeui-stage/package.json \"$PROJ/package.json\" && ");
    sb.append(
        "[ ! -f /root/.nodeui-stage/package-lock.json ] || cp -f /root/.nodeui-stage/package-lock.json \"$PROJ/package-lock.json\"");
    return sb.toString();
  }

  private String installTarget() {
    return jsTarget != null ? jsTarget.getAbsolutePath() : null;
  }

  private void run(String command, String workDir, String title, String echo) {
    if (!busy.compareAndSet(false, true)) {
      toast(getString(R.string.nodeui_toast_busy));
      return;
    }
    setButtonsEnabled(false);
    setStatus(getString(R.string.nodeui_status_running), Palette.primary());
    appendLog(getString(R.string.nodeui_log_command, echo));

    worker.execute(
        () -> {
          try {
            ProotShell.Result result =
                ProotShell.run(context, command, workDir, line -> main.post(() -> appendLog(line)));
            main.post(
                () -> {
                  if (result.isSuccess()) {
                    appendLog(getString(R.string.nodeui_log_done, title));
                  } else {
                    appendLog(getString(R.string.nodeui_log_failed, title, result.exitCode));
                  }
                  finish(title, result.isSuccess());
                });
          } catch (Exception e) {
            String message = e.getMessage();
            main.post(
                () -> {
                  appendLog(
                      getString(R.string.nodeui_log_error_prefix)
                          + (message == null ? e.getClass().getSimpleName() : message));
                  finish(title, false);
                });
          }
        });
  }

  private void finish(String title, boolean success) {
    busy.set(false);
    setButtonsEnabled(true);
    setStatus(
        getString(success ? R.string.nodeui_status_success : R.string.nodeui_status_failure),
        success ? Palette.success() : Palette.error());
    toast(getString(success ? R.string.nodeui_toast_success : R.string.nodeui_toast_failure, title));
    refreshEnvironment();
  }

  private void setButtonsEnabled(boolean enabled) {
    searchButton.setEnabled(enabled);
    installButton.setEnabled(enabled);
    resultInstall.setEnabled(enabled);
    clearButton.setEnabled(enabled);
  }

  private void appendLog(String line) {
    logLines.addLast(line);
    while (logLines.size() > MAX_LOG_LINES) {
      logLines.removeFirst();
    }
    renderLog();
  }

  private void renderLog() {
    SpannableStringBuilder sb = new SpannableStringBuilder();
    for (String line : logLines) {
      int start = sb.length();
      sb.append(line);
      sb.setSpan(
          new ForegroundColorSpan(logColor(line)),
          start,
          sb.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      sb.append('\n');
    }
    logView.setText(sb);
    logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
  }

  private int logColor(String line) {
    if (line.isEmpty()) {
      return Palette.onSurface();
    }
    char marker = line.charAt(0);
    if (marker == '\u2714') return Palette.success();
    if (marker == '\u2718') return Palette.error();
    if (marker == '$') return Palette.primary();
    if (line.startsWith("[JS]")) return Palette.primary();
    if (line.startsWith(getString(R.string.nodeui_log_error_prefix))) return Palette.error();
    return Palette.onSurface();
  }

  private void clearLog() {
    logLines.clear();
    logView.setText("");
  }

  private void toast(String message) {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message, false);
    }
  }

  private String getString(int resId, Object... args) {
    return context.getString(resId, args);
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
