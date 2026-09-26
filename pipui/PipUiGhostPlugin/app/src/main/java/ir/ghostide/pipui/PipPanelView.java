package ir.ghostide.pipui;

import android.content.Context;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

final class PipPanelView {

  private static final String PYTHON_GUARD =
      "if ! command -v python3 >/dev/null 2>&1; then "
          + "apt update && apt install python3-full python3-pip -y; fi; "
          + "if ! python3 -m pip --version >/dev/null 2>&1; then "
          + "apt update && apt install python3-pip -y; fi; ";

  private static final String PIP = "python3 -m pip";

  private static final String BREAK = "--break-system-packages";

  private static final int MAX_LOG_LINES = 400;

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

  private final Context context;
  private final PluginContext plugin;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean(false);
  private final ArrayDeque<String> logLines = new ArrayDeque<>();

  private final View root;
  private final EditText searchField;
  private final EditText commandField;
  private final LinearLayout resultCard;
  private final TextView statusLabel;
  private final TextView logView;
  private final View listButton;
  private final View clearButton;

  private PyPiClient.Package pending;

  PipPanelView(Context context, PluginContext plugin) {
    this.context = context;
    this.plugin = plugin;

    this.searchField = field("نام پکیج را بنویس، مثلا requests یا fastapi");
    this.commandField = field("یا هر دستور pip دلخواه، مثلا: install httpx");

    LinearLayout content = column();
    content.setPadding(dp(18), dp(14), dp(18), dp(20));

    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout titles = column();
    TextView title = label("Pip Installer", 20, true);
    title.setTextColor(C_INSTALL);
    titles.addView(title);
    TextView sub = label("نصب کتابخانه‌های پایتون بدون ترمینال — داخل همان Debian", 12, false);
    sub.setTextColor(C_HINT);
    titles.addView(sub);
    header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    this.statusLabel = label("", 12, true);
    header.addView(statusLabel);
    content.addView(header, gap(14));

    content.addView(section("جستجو در PyPI", C_SEARCH));
    LinearLayout searchCard = column();
    searchCard.addView(searchField);
    searchCard.addView(
        buttonRow(
            "جستجو", C_SEARCH,
            v -> search(),
            "نصب مستقیم", C_INSTALL,
            v -> installTyped(searchField.getText().toString().trim())),
        gap(8));
    this.resultCard = column();
    this.resultCard.setVisibility(View.GONE);
    searchCard.addView(this.resultCard, gap(8));
    content.addView(searchCard, gap(16));

    content.addView(section("دستور دلخواه", C_DIRECT));
    LinearLayout commandCard = column();
    commandCard.addView(commandField);
    commandCard.addView(
        buttonRow(
            "اجرا در پروت", C_DIRECT,
            v -> installTyped(commandField.getText().toString().trim()),
            "باز کردن در ترمینال", C_TERMINAL,
            v -> openInTerminal(commandField.getText().toString().trim())),
        gap(8));
    content.addView(commandCard, gap(16));

    content.addView(section("پکیج‌های پرکاربرد", C_CATALOG));
    LinearLayout catalog = column();
    buildCatalog(catalog);
    content.addView(catalog, gap(16));

    content.addView(section("خروجی", C_LIST));
    LinearLayout logCard = column();
    this.logView = new TextView(context);
    this.logView.setTextSize(11);
    this.logView.setTypeface(Typeface.MONOSPACE);
    this.logView.setTextIsSelectable(true);
    this.logView.setMinLines(6);
    this.logView.setTextColor(C_TEXT);
    this.clearButton = button("پاک کردن خروجی", C_CLEAR, v -> clearLog());
    this.listButton =
        button(
            "فهرست نصب‌شده‌ها",
            C_LIST,
            v -> run(PIP + " list", "pip list", "pip3 list --format=columns"));
    logCard.addView(logView);
    logCard.addView(buttonRowHolder(clearButton, listButton), gap(8));
    content.addView(logCard);

    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.addView(
        content,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    this.root = scroll;

    appendLog("یک پکیج را سرچ کن یا از لیست زیر انتخاب کن.");
    refreshEnvironment();
  }

  View getRoot() {
    return root;
  }

  private void refreshEnvironment() {
    worker.execute(
        () -> {
          boolean rootfs = ProotShell.isInstalled(context);
          boolean python = rootfs && ProotShell.hasBinary(context, "/usr/bin/python3");
          main.post(
              () -> {
                if (!rootfs) {
                  setStatus("Debian نصب نیست", C_CLEAR);
                } else if (!python) {
                  setStatus("پایتون نصب نیست", C_TERMINAL);
                } else {
                  setStatus("آماده", C_INSTALL);
                }
              });
        });
  }

  private void setStatus(String text, int color) {
    statusLabel.setText(text);
    statusLabel.setTextColor(color);
  }

  private void search() {
    String query = searchField.getText().toString().trim();
    if (query.isEmpty()) {
      resultCard.setVisibility(View.GONE);
      toast("اول یک نام پکیج بنویس");
      return;
    }
    resultCard.setVisibility(View.VISIBLE);
    resultCard.removeAllViews();
    TextView loading = label("در حال جستجو…", 13, false);
    loading.setTextColor(C_SEARCH);
    resultCard.addView(loading);
    worker.execute(
        () -> {
          PyPiClient.Result result = PyPiClient.lookup(query);
          main.post(() -> showResult(query, result));
        });
  }

  private void showResult(String query, PyPiClient.Result result) {
    resultCard.removeAllViews();
    if (!result.isFound()) {
      TextView err = label(result.error, 13, true);
      err.setTextColor(C_CLEAR);
      resultCard.addView(err);
      TextView hint = label("با «نصب مستقیم» می‌توانی همین متن را به pip بدهی.", 12, false);
      hint.setTextColor(C_HINT);
      resultCard.addView(hint, gap(6));
      return;
    }

    PyPiClient.Package pkg = result.pkg;
    this.pending = pkg;

    TextView name = label(pkg.name, 16, true);
    name.setTextColor(C_SEARCH);
    resultCard.addView(name);
    if (!pkg.version.isEmpty()) {
      TextView v = label("نسخه " + pkg.version, 12, true);
      v.setTextColor(C_CATALOG);
      resultCard.addView(v);
    }
    if (!pkg.summary.isEmpty()) {
      TextView s = label(pkg.summary, 13, false);
      s.setTextColor(C_TEXT);
      resultCard.addView(s, gap(6));
    }
    if (!pkg.author.isEmpty()) {
      TextView a = label("نویسنده: " + pkg.author, 12, false);
      a.setTextColor(C_HINT);
      resultCard.addView(a);
    }
    if (!pkg.requiresPython.isEmpty()) {
      TextView p = label("نیازمند پایتون: " + pkg.requiresPython, 12, false);
      p.setTextColor(C_HINT);
      resultCard.addView(p);
    }
    String requirement = requirement(query, pkg.name);
    resultCard.addView(
        button(
            "نصب " + requirement,
            C_INSTALL,
            v -> {
              if (pending != null) {
                install(requirement, "نصب " + pending.name);
              }
            }),
        gap(8));
  }

  private static String requirement(String query, String resolvedName) {
    String typed = query.trim();
    String base = PyPiClient.baseName(typed);
    if (base.equalsIgnoreCase(resolvedName) && typed.length() > base.length()) {
      return resolvedName + typed.substring(base.length());
    }
    return resolvedName;
  }

  private void installTyped(String rawCommand) {
    if (rawCommand.isEmpty()) {
      toast("اول یک پکیج بنویس");
      return;
    }
    if (rawCommand.startsWith(PIP)
        || rawCommand.startsWith("pip3 ")
        || rawCommand.startsWith("pip ")) {
      String withFlag = ensureBreakFlag(rawCommand);
      run(withFlag, "اجرای دستور", withFlag);
      return;
    }
    String requirement = rawCommand;
    if (requirement.startsWith("install ")) {
      requirement = requirement.substring("install ".length()).trim();
    }
    install(requirement, "نصب " + requirement);
  }
private void install(String requirement, String title) {
  String cleanRequirement =
      requirement == null
          ? ""
          : requirement.trim();

  if (cleanRequirement.isEmpty()) {
    toast("نام پکیج خالی است");
    return;
  }

  String cmd =
      PIP
          + " install "
          + cleanRequirement
          + " "
          + BREAK;

  String fullCommand =
      PYTHON_GUARD
          + cmd;

  run(
      fullCommand,
      title,
      cmd);
}
  private static String ensureBreakFlag(String command) {
    String cmd = command.trim();
    if (cmd.contains(BREAK)) {
      return cmd;
    }
    if (cmd.startsWith("install ")) {
      return "install " + BREAK + " " + cmd.substring("install ".length()).trim();
    }
    int idx = cmd.indexOf(" install ");
    if (idx >= 0) {
      String head = cmd.substring(0, idx + " install".length());
      String tail = cmd.substring(idx + " install".length()).trim();
      return head + " " + BREAK + " " + tail;
    }
    return cmd;
  }

  private void openInTerminal(String command) {
    if (command.isEmpty()) {
      toast("اول یک دستور بنویس");
      return;
    }
    if (!ProotShell.isInstalled(context)) {
      toast("Debian هنوز بوت نشده");
      return;
    }
    String full;
    if (command.startsWith("pip")) {
      full = ensureBreakFlag(command);
    } else {
      String rest =
          command.startsWith("install ")
              ? command.substring("install ".length()).trim()
              : command;
      full = PIP + " install " + BREAK + " " + rest;
    }
    try {
      android.content.Intent intent =
          new android.content.Intent("ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity");
      intent.putExtra("command", PYTHON_GUARD + full);
      intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    } catch (RuntimeException e) {
      plugin.getLogger().warn("Terminal intent failed", e);
      toast("ترمینال در دسترس نیست");
    }
  }

  private void run(String command, String title, String echo) {
    if (!busy.compareAndSet(false, true)) {
      toast("یک عملیات در حال اجراست");
      return;
    }
    setButtonsEnabled(false);
    setStatus("در حال اجرا", C_TERMINAL);
    appendLog("$ " + echo);

    worker.execute(
        () -> {
          try {
            ProotShell.Result result =
                ProotShell.run(context, command, line -> main.post(() -> appendLog(line)));
            main.post(
                () -> {
                  if (result.isSuccess()) {
                    appendLog("✔ " + title + " انجام شد");
                  } else {
                    appendLog("✘ " + title + " با کد " + result.exitCode + " شکست خورد");
                  }
                  finish(title, result.isSuccess());
                });
          } catch (Exception e) {
            String message = e.getMessage();
            main.post(
                () -> {
                  appendLog("خطا: " + (message == null ? e.getClass().getSimpleName() : message));
                  finish(title, false);
                });
          }
        });
  }

  private void finish(String title, boolean success) {
    busy.set(false);
    setButtonsEnabled(true);
    setStatus(success ? "موفق" : "ناموفق", success ? C_INSTALL : C_CLEAR);
    toast((success ? "موفق " : "خطا ") + title);
    refreshEnvironment();
  }

  private void buildCatalog(LinearLayout container) {
    List<PackageCatalog.Group> groups = PackageCatalog.groups();
    for (int g = 0; g < groups.size(); g++) {
      PackageCatalog.Group group = groups.get(g);
      TextView groupTitle = label(group.title, 14, true);
      groupTitle.setTextColor(C_CATALOG);
      container.addView(groupTitle, gap(g == 0 ? 0 : 12));
      LinearLayout card = column();
      for (int e = 0; e < group.entries.size(); e++) {
        card.addView(entryRow(group.entries.get(e)), gap(e == 0 ? 0 : 6));
      }
      container.addView(card, gap(4));
    }
  }

  private LinearLayout entryRow(PackageCatalog.Entry entry) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);

    LinearLayout titles = column();
    TextView name = label(entry.name, 14, true);
    name.setTextColor(C_TEXT);
    titles.addView(name);
    TextView summary = label(entry.summary, 12, false);
    summary.setTextColor(C_HINT);
    titles.addView(summary);
    row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(
        button(
            "نصب",
            C_CATALOG,
            v -> {
              searchField.setText(entry.name);
              install(entry.requirement, "نصب " + entry.name);
            }));
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
  }

  private static int logColor(String line) {
    if (line.startsWith("✔")) return C_INSTALL;
    if (line.startsWith("✘")) return C_CLEAR;
    if (line.startsWith("خطا")) return C_CLEAR;
    if (line.startsWith("$")) return C_SEARCH;
    return C_TEXT;
  }

  private void clearLog() {
    logLines.clear();
    logView.setText("");
  }

  private void setButtonsEnabled(boolean enabled) {
    clearButton.setEnabled(enabled);
    listButton.setEnabled(enabled);
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

  private TextView label(String text, float size, boolean bold) {
    TextView view = new TextView(context);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(C_TEXT);
    if (bold) {
      view.setTypeface(view.getTypeface(), Typeface.BOLD);
    }
    return view;
  }

  private TextView section(String text, int color) {
    TextView view = label(text, 14, true);
    view.setTextColor(color);
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

  private LinearLayout buttonRow(
      String left, int leftColor, View.OnClickListener leftListener,
      String right, int rightColor, View.OnClickListener rightListener) {
    return buttonRowHolder(button(left, leftColor, leftListener), button(right, rightColor, rightListener));
  }

  private LinearLayout buttonRowHolder(View left, View right) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(space(10));
    row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private View space(int width) {
    View view = new View(context);
    view.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
    return view;
  }

  private LinearLayout.LayoutParams gap(int height) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(height);
    return params;
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}