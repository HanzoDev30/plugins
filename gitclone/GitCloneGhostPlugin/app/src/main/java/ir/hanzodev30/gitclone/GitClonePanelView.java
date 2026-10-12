package ir.hanzodev30.gitclone;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ir.hanzodev1375.ghostide.ide.ui.api.FileManagerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

final class GitClonePanelView {

  private static final int MAX_LOG_LINES = 400;

  private final Context context;
  private final PluginContext plugin;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicInteger lastPostedPercent = new AtomicInteger(-2);
  private final ArrayDeque<String> logLines = new ArrayDeque<>();

  private final View root;
  private final TextView statusLabel;
  private final EditText urlField;
  private final EditText targetField;
  private final EditText branchField;
  private final EditText tokenField;
  private final List<OptionChip> chips = new ArrayList<>();
  private final ProgressBar progress;
  private final TextView progressLabel;
  private final Button cloneButton;
  private final Button cancelButton;
  private final TextView logView;
  private final ScrollView logScroll;

  private volatile JgitProgressMonitor activeMonitor;

  GitClonePanelView(Context hostContext, PluginContext plugin) {
    this.context = hostContext;
    this.plugin = plugin;

    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);

    LinearLayout rootLayout = new LinearLayout(context);
    rootLayout.setOrientation(LinearLayout.VERTICAL);
    rootLayout.setPadding(dp(20), dp(18), dp(20), dp(22));
    scroll.addView(
        rootLayout,
        new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
    root = scroll;

    LinearLayout.LayoutParams wrap = matchWrap();

    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    rootLayout.addView(header, wrap);

    LinearLayout titles = new LinearLayout(context);
    titles.setOrientation(LinearLayout.VERTICAL);
    header.addView(
        titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    TextView title = new TextView(context);
    title.setText(getString(R.string.gitclone_app_name));
    title.setTextSize(20f);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setTextColor(Palette.primary());
    titles.addView(title, wrap);

    TextView subtitle = new TextView(context);
    subtitle.setText(getString(R.string.gitclone_subtitle));
    subtitle.setTextSize(12f);
    subtitle.setTextColor(Palette.onSurfaceVariant());
    titles.addView(subtitle, wrap);

    statusLabel = new TextView(context);
    statusLabel.setTextSize(12f);
    statusLabel.setTypeface(Typeface.DEFAULT_BOLD);
    statusLabel.setGravity(Gravity.END);
    statusLabel.setTextColor(Palette.onSurfaceVariant());
    header.addView(statusLabel, wrap);

    rootLayout.addView(space(dp(16)));

    addLabel(rootLayout, getString(R.string.gitclone_label_url));
    urlField = field();
    urlField.setHint(getString(R.string.gitclone_hint_url));
    urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    rootLayout.addView(urlField, wrap);

    rootLayout.addView(space(dp(12)));

    addLabel(rootLayout, getString(R.string.gitclone_label_target));
    targetField = field();
    targetField.setHint(getString(R.string.gitclone_hint_target));
    targetField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    rootLayout.addView(targetField, wrap);

    rootLayout.addView(space(dp(12)));

    LinearLayout fieldsRow = new LinearLayout(context);
    fieldsRow.setOrientation(LinearLayout.HORIZONTAL);
    rootLayout.addView(fieldsRow, wrap);

    branchField = field();
    branchField.setHint(getString(R.string.gitclone_hint_branch));
    branchField.setLayoutParams(
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    fieldsRow.addView(branchField);

    fieldsRow.addView(space(dp(10)));

    tokenField = field();
    tokenField.setHint(getString(R.string.gitclone_hint_token));
    tokenField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    tokenField.setLayoutParams(
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    fieldsRow.addView(tokenField);

    rootLayout.addView(space(dp(14)));

    addLabel(rootLayout, getString(R.string.gitclone_label_options));
    LinearLayout chipsHost = new LinearLayout(context);
    chipsHost.setOrientation(LinearLayout.VERTICAL);
    rootLayout.addView(chipsHost, wrap);

    LinearLayout row1 = new LinearLayout(context);
    row1.setOrientation(LinearLayout.HORIZONTAL);
    chipsHost.addView(row1, wrap);
    addChip(row1, getString(R.string.gitclone_chip_submodules));
    addChip(row1, getString(R.string.gitclone_chip_shallow));
    addChip(row1, getString(R.string.gitclone_chip_single));

    LinearLayout row2 = new LinearLayout(context);
    row2.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams row2Lp = matchWrap();
    row2Lp.topMargin = dp(8);
    chipsHost.addView(row2, row2Lp);
    addChip(row2, getString(R.string.gitclone_chip_notags));
    addChip(row2, getString(R.string.gitclone_chip_bare));

    rootLayout.addView(space(dp(18)));

    progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    progress.setProgress(0);
    progress.setProgressTintList(ColorStateList.valueOf(Palette.primary()));
    progress.setProgressBackgroundTintList(ColorStateList.valueOf(Palette.surfaceContainerHigh()));
    progress.setIndeterminateTintList(ColorStateList.valueOf(Palette.primary()));
    rootLayout.addView(
        progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

    progressLabel = new TextView(context);
    progressLabel.setTextSize(12f);
    progressLabel.setTextColor(Palette.onSurfaceVariant());
    progressLabel.setPadding(0, dp(6), 0, 0);
    rootLayout.addView(progressLabel, wrap);

    rootLayout.addView(space(dp(14)));

    cloneButton = new Button(context);
    cloneButton.setText(getString(R.string.gitclone_action_clone));
    cloneButton.setTextSize(15f);
    cloneButton.setTypeface(Typeface.DEFAULT_BOLD);
    cloneButton.setTextColor(Palette.onPrimary());
    cloneButton.setBackground(rounded(Palette.primary(), dp(14)));
    cloneButton.setMinHeight(dp(52));
    rootLayout.addView(cloneButton, wrap);

    cancelButton = new Button(context);
    cancelButton.setText(getString(R.string.gitclone_action_cancel));
    cancelButton.setTextSize(14f);
    cancelButton.setTextColor(Palette.error());
    cancelButton.setBackgroundColor(0);
    cancelButton.setVisibility(View.GONE);
    rootLayout.addView(cancelButton, wrap);

    rootLayout.addView(space(dp(18)));

    LinearLayout outputHeader = new LinearLayout(context);
    outputHeader.setOrientation(LinearLayout.HORIZONTAL);
    outputHeader.setGravity(Gravity.CENTER_VERTICAL);
    rootLayout.addView(outputHeader, wrap);

    TextView outputTitle = new TextView(context);
    outputTitle.setText(getString(R.string.gitclone_section_output));
    outputTitle.setTextSize(14f);
    outputTitle.setTypeface(Typeface.DEFAULT_BOLD);
    outputTitle.setTextColor(Palette.primary());
    outputHeader.addView(
        outputTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    Button clearButton = new Button(context);
    clearButton.setText(getString(R.string.gitclone_action_clear_output));
    clearButton.setTextSize(12f);
    clearButton.setTextColor(Palette.primary());
    clearButton.setBackgroundColor(0);
    outputHeader.addView(clearButton, wrap);

    logScroll = new ScrollView(context);
    GradientDrawable logBg = rounded(Palette.surfaceContainer(), dp(12));
    logBg.setStroke(dp(1), Palette.outlineVariant());
    logScroll.setBackground(logBg);
    logScroll.setPadding(dp(12), dp(8), dp(12), dp(8));
    rootLayout.addView(
        logScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));

    logView = new TextView(context);
    logView.setTextSize(11f);
    logView.setTypeface(Typeface.MONOSPACE);
    logView.setTextColor(Palette.onSurface());
    logView.setTextIsSelectable(true);
    logScroll.addView(
        logView,
        new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

    applyLayoutDirection();

    cloneButton.setOnClickListener(v -> startClone());
    cancelButton.setOnClickListener(v -> cancel());
    clearButton.setOnClickListener(v -> clearLog());

    appendLog(getString(R.string.gitclone_log_intro));
    resetProgress();
  }

  View getRoot() {
    return root;
  }

  void shutdown() {
    cancelled.set(true);
    JgitProgressMonitor monitor = activeMonitor;
    if (monitor != null) {
      monitor.cancel();
    }
    worker.shutdownNow();
  }

  private LinearLayout.LayoutParams matchWrap() {
    return new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
  }

  private void addLabel(LinearLayout parent, String text) {
    TextView label = new TextView(context);
    label.setText(text);
    label.setTextSize(11f);
    label.setTypeface(Typeface.DEFAULT_BOLD);
    label.setTextColor(Palette.onSurfaceVariant());
    LinearLayout.LayoutParams lp = matchWrap();
    lp.bottomMargin = dp(6);
    parent.addView(label, lp);
  }

  private EditText field() {
    EditText edit = new EditText(context);
    edit.setTextSize(15f);
    edit.setSingleLine(true);
    edit.setGravity(Gravity.CENTER_VERTICAL);
    edit.setTextColor(Palette.onSurface());
    edit.setHintTextColor(Palette.onSurfaceVariant());
    edit.setMinHeight(dp(52));
    edit.setPadding(dp(16), dp(14), dp(16), dp(14));
    GradientDrawable bg = rounded(Palette.surfaceContainerHigh(), dp(12));
    bg.setStroke(dp(1), Palette.outlineVariant());
    edit.setBackground(bg);
    return edit;
  }

  private void addChip(LinearLayout parent, String text) {
    OptionChip chip = new OptionChip(context, text);
    chips.add(chip);
    parent.addView(
        chip,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    parent.addView(space(dp(8)));
  }

  private Space space(int size) {
    Space space = new Space(context);
    space.setLayoutParams(new LinearLayout.LayoutParams(size, size));
    return space;
  }

  private GradientDrawable rounded(int color, int radius) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(color);
    bg.setCornerRadius(radius);
    return bg;
  }

  private void applyLayoutDirection() {
    root.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
  }

  private void startClone() {
    if (!busy.compareAndSet(false, true)) {
      toast(getString(R.string.gitclone_toast_busy));
      return;
    }

    final String url = urlField.getText().toString().trim();
    if (url.isEmpty()) {
      busy.set(false);
      toast(getString(R.string.gitclone_toast_no_url));
      return;
    }

    final String branch = branchField.getText().toString().trim();
    final String token = tokenField.getText().toString().trim();
    final boolean submodules = chips.get(0).isChecked();
    final boolean shallow = chips.get(1).isChecked();
    final boolean single = chips.get(2).isChecked();
    final boolean noTags = chips.get(3).isChecked();
    final boolean bare = chips.get(4).isChecked();

    final File target = resolveTarget(url);
    if (target.exists() && target.isDirectory() && isNonEmpty(target)) {
      busy.set(false);
      toast(getString(R.string.gitclone_error_exists, target.getAbsolutePath()));
      return;
    }

    cancelled.set(false);
    activeMonitor = new JgitProgressMonitor(this::onPhase);
    setRunning(true);
    appendLog(getString(R.string.gitclone_log_target, target.getAbsolutePath()));
    appendLog(getString(R.string.gitclone_log_command, url));
    setStatus(getString(R.string.gitclone_status_running), Palette.primary());

    worker.execute(
        () -> clone(url, target, branch, token, submodules, shallow, single, noTags, bare));
  }

  private void clone(
      String url,
      File target,
      String branch,
      String token,
      boolean submodules,
      boolean shallow,
      boolean single,
      boolean noTags,
      boolean bare) {
    JgitProgressMonitor monitor = activeMonitor;
    boolean success = false;
    String failure = null;
    try {
      CloneCommand command = Git.cloneRepository();
      command.setURI(url);
      command.setDirectory(target);
      command.setProgressMonitor(monitor);
      command.setCloneSubmodules(submodules);
      command.setBare(bare);
      if (shallow) {
        command.setDepth(1);
      }
      if (single) {
        command.setCloneAllBranches(false);
        if (!branch.isEmpty()) {
          command.setBranchesToClone(Collections.singletonList("refs/heads/" + branch));
        }
      }
      if (!branch.isEmpty()) {
        command.setBranch(branch);
      }
      if (noTags) {
        command.setNoTags();
      }
      if (!token.isEmpty()) {
        command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""));
      }

      Git git = command.call();
      if (git != null) {
        git.close();
      }
      success = true;
    } catch (Exception e) {
      failure = e.getMessage();
      if (failure == null || failure.isEmpty()) {
        failure = e.getClass().getSimpleName();
      }
    }

    final boolean ok = success;
    final String error = failure;
    main.post(() -> finish(ok, error, target));
  }

  private void finish(boolean success, @Nullable String error, File target) {
    boolean wasCancelled = cancelled.get();
    activeMonitor = null;
    busy.set(false);
    setRunning(false);

    if (success) {
      setStatus(getString(R.string.gitclone_status_success), Palette.success());
      appendLog(getString(R.string.gitclone_log_done, target.getAbsolutePath()));
      completeProgress();
      toast(getString(R.string.gitclone_toast_success, target.getAbsolutePath()));
      refreshFileManager();
    } else if (wasCancelled) {
      setStatus(getString(R.string.gitclone_status_cancelled), Palette.warning());
      appendLog(getString(R.string.gitclone_log_cancelled));
      resetProgress();
    } else {
      setStatus(
          error == null ? getString(R.string.gitclone_status_failure) : error, Palette.error());
      appendLog(getString(R.string.gitclone_log_error_prefix) + (error == null ? "" : error));
      resetProgress();
      toast(getString(R.string.gitclone_toast_failure));
    }
  }

  private void cancel() {
    if (!busy.get()) {
      return;
    }
    cancelled.set(true);
    JgitProgressMonitor monitor = activeMonitor;
    if (monitor != null) {
      monitor.cancel();
    }
  }

  private void setRunning(boolean running) {
    urlField.setEnabled(!running);
    targetField.setEnabled(!running);
    branchField.setEnabled(!running);
    tokenField.setEnabled(!running);
    for (OptionChip chip : chips) {
      chip.setEnabled(!running);
    }
    cloneButton.setEnabled(!running);
    cancelButton.setVisibility(running ? View.VISIBLE : View.GONE);

    if (running) {
      lastPostedPercent.set(-2);
      progress.setIndeterminate(true);
      setStatus(getString(R.string.gitclone_status_running), Palette.primary());
    }
  }

  private void onPhase(String title, int percent) {
    main.post(() -> updateProgress(title, percent));
  }

  private void updateProgress(String title, int percent) {
    String phase = title == null || title.isEmpty() ? "Working\u2026" : title;
    if (percent < 0) {
      if (lastPostedPercent.get() != -1) {
        lastPostedPercent.set(-1);
        progress.setIndeterminate(true);
      }
      progressLabel.setText(phase);
      return;
    }
    int clamped = Math.max(0, Math.min(100, percent));
    if (lastPostedPercent.get() == clamped) {
      return;
    }
    lastPostedPercent.set(clamped);
    progress.setIndeterminate(false);
    progress.setProgress(clamped);
    progressLabel.setText(getString(R.string.gitclone_progress_phase, phase, clamped));
  }

  private void completeProgress() {
    progress.setIndeterminate(false);
    progress.setProgress(100);
    progressLabel.setText(getString(R.string.gitclone_progress_done));
  }

  private void resetProgress() {
    lastPostedPercent.set(-2);
    progress.setIndeterminate(false);
    progress.setProgress(0);
    progressLabel.setText(getString(R.string.gitclone_progress_idle));
  }

  private void setStatus(String text, int color) {
    statusLabel.setText(text);
    statusLabel.setTextColor(color);
  }

  private File resolveTarget(String url) {
    String typed = targetField.getText().toString().trim();
    if (!typed.isEmpty()) {
      return new File(typed);
    }
    String name = repoName(url);
    File base = null;
    FileManagerHost fileManager = null;
    if (plugin != null) {
      fileManager = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
    }
    if (fileManager != null) {
      File selected = fileManager.getSelectedFile();
      if (selected != null && selected.isDirectory()) {
        base = selected;
      } else if (fileManager.getRootDirectory() != null) {
        base = fileManager.getRootDirectory();
      }
    }
    if (base == null) {
      base = new File("/sdcard");
    }
    return new File(base, name);
  }

  private static String repoName(String url) {
    String clean = url.trim();
    while (clean.endsWith("/")) {
      clean = clean.substring(0, clean.length() - 1);
    }
    int slash = clean.lastIndexOf('/');
    String name = slash >= 0 ? clean.substring(slash + 1) : clean;
    int colon = name.lastIndexOf(':');
    if (colon >= 0) {
      name = name.substring(colon + 1);
    }
    if (name.toLowerCase(Locale.ROOT).endsWith(".git")) {
      name = name.substring(0, name.length() - 4);
    }
    if (name.isEmpty()) {
      name = "repository";
    }
    return name;
  }

  private void refreshFileManager() {
    if (plugin == null) {
      return;
    }
    FileManagerHost fileManager = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
    if (fileManager != null) {
      fileManager.refresh();
    }
  }

  private static boolean isNonEmpty(File dir) {
    File[] children = dir.listFiles();
    return children != null && children.length > 0;
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
    if (marker == '\u2714') {
      return Palette.success();
    }
    if (marker == '\u2718') {
      return Palette.error();
    }
    if (marker == '$') {
      return Palette.primary();
    }
    if (line.startsWith(getString(R.string.gitclone_log_error_prefix))) {
      return Palette.error();
    }
    return Palette.onSurface();
  }

  private void clearLog() {
    logLines.clear();
    logView.setText("");
  }

  private void toast(String message) {
    if (plugin == null) {
      return;
    }
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

  static final class OptionChip extends TextView {

    private final int uncheckedBg;
    private final int checkedBg;
    private boolean checked;

    OptionChip(Context context, String text) {
      super(context);
      this.uncheckedBg = Palette.surfaceContainerHigh();
      this.checkedBg = Palette.primary();

      setText(text);
      setTextSize(13f);
      setTextColor(Palette.onSurface());
      setGravity(Gravity.CENTER);
      setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9));
      setClickable(true);
      setMinimumHeight(dp(context, 38));

      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(context, 22));
      bg.setColor(uncheckedBg);
      bg.setStroke(dp(context, 1), Palette.outlineVariant());
      setBackground(bg);

      setOnClickListener(v -> setChecked(!isChecked()));
    }

    boolean isChecked() {
      return checked;
    }

    void setChecked(boolean value) {
      if (this.checked == value) {
        return;
      }
      this.checked = value;
      applyState();
    }

    private void applyState() {
      int start = checked ? uncheckedBg : checkedBg;
      int end = checked ? checkedBg : uncheckedBg;
      int text = checked ? Palette.onPrimary() : Palette.onSurface();

      ValueAnimator anim = ValueAnimator.ofArgb(start, end);
      anim.setDuration(180);
      anim.addUpdateListener(
          a -> ((GradientDrawable) getBackground()).setColor((Integer) a.getAnimatedValue()));
      anim.start();
      setTextColor(text);
    }

    private static int dp(Context context, int value) {
      return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
  }
}
