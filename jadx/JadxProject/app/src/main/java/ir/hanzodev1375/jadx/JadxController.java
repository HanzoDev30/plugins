package ir.hanzodev1375.jadx;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileManagerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.jadx.bridge.JadxEngine;
import ir.hanzodev1375.jadx.bridge.JadxSession;
import ir.hanzodev1375.jadx.bridge.SaveResult;
import ir.theme.M3Theme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All the UI flows of the plugin:
 *
 * <pre>
 * toolbar icon -> list of .dex files in the folder -> tap one -> class browser -> tap a class -> code
 * tap on a .dex file -----------------------------> class browser          \-> "Decompile all"
 * </pre>
 *
 * Heavy work (scanning, loading dex, decompiling) runs on background threads; every dialog is
 * touched on the main thread only.
 */
final class JadxController {

  private static final int CODE_PREVIEW_LIMIT = 150_000;

  private final PluginContext plugin;
  private final Context pluginContext;
  private final JadxSettings settings;
  private final EngineHolder engines;

  private final Handler main = new Handler(Looper.getMainLooper());
  /** Everything that touches a jadx session. Big stack: jadx recurses deeply on huge methods. */
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(named("jadx-worker", 64L * 1024L * 1024L));
  /** Folder scans and cancel requests, so they never queue behind a long decompile. */
  private final ExecutorService helper = Executors.newCachedThreadPool(named("jadx-helper", 0L));

  private JadxSession current;
  private File currentWork;
  private volatile boolean shutdown;

  JadxController(
      PluginContext plugin, Context pluginContext, JadxSettings settings, EngineHolder engines) {
    this.plugin = plugin;
    this.pluginContext = pluginContext;
    this.settings = settings;
    this.engines = engines;
  }

  // ------------------------------------------------------------------ entry points

  /** File manager toolbar icon. */
  void onToolbarClick(View anchor) {
    Context context = anchor.getContext();
    FileManagerHost files = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);

    File folder = null;
    if (files != null) {
      File selected = files.getSelectedFile();
      if (selected != null) {
        folder = selected.isDirectory() ? selected : selected.getParentFile();
      }
      if (folder == null) {
        folder = files.getRootDirectory();
      }
    }
    if (folder == null || !folder.isDirectory()) {
      toast(L.t("Open a folder in the file manager first", "ابتدا یک پوشه را در فایل‌منیجر باز کن"));
      return;
    }
    showDexList(context, folder);
  }

  /** Tap on a .dex file in the file manager. */
  void onDexTap(Context context, File dex) {
    if (dex == null || !dex.isFile()) {
      toast(L.t("File not found", "فایل پیدا نشد"));
      return;
    }
    openDex(context, dex);
  }

  void shutdown() {
    if (shutdown) {
      return;
    }
    shutdown = true;
    helper.shutdownNow();
    worker.execute(this::closeCurrent);
    worker.shutdown();
  }

  // ------------------------------------------------------------------ step 1: list of dex files

  private void showDexList(Context context, File folder) {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0);

    TextView path = Ui.text(context, folder.getAbsolutePath(), 12, Ui.onSurfaceVariant(), false);
    path.setTextDirection(View.TEXT_DIRECTION_LTR);
    TextView status =
        Ui.text(
            context,
            L.t("Searching for .dex files...", "در حال جستجوی فایل‌های .dex ..."),
            14,
            Ui.onSurface(),
            false);
    status.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8));

    ProgressBar spinner = new ProgressBar(context);
    spinner.setIndeterminate(true);
    M3Theme.progressBar(spinner);

    ListView list = new ListView(context);
    list.setVisibility(View.GONE);
    list.setDividerHeight(0);

    box.addView(path);
    box.addView(status);
    box.addView(spinner);
    box.addView(
        list,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 320)));

    AtomicBoolean cancelled = new AtomicBoolean(false);
    AlertDialog dialog =
        new DialogCompat(context)
            .setTitle(L.t("DEX files", "فایل‌های DEX"))
            .setView(box)
            .setCancelable(true)
            .setNegativeButton(L.t("Close", "بستن"), null)
            .create();
    dialog.setOnDismissListener(d -> cancelled.set(true));
    dialog.show();

    helper.execute(
        () -> {
          List<DexEntry> found = DexScanner.scan(folder, cancelled);
          main.post(
              () -> {
                if (!dialog.isShowing()) {
                  return;
                }
                spinner.setVisibility(View.GONE);
                if (found.isEmpty()) {
                  status.setText(
                      L.t("No .dex file found in this folder.", "هیچ فایل .dex در این پوشه نبود."));
                  return;
                }
                status.setText(
                    L.t(found.size() + " dex file(s) found", found.size() + " فایل dex پیدا شد"));
                list.setAdapter(new DexAdapter(context, folder, found));
                list.setVisibility(View.VISIBLE);
                list.setOnItemClickListener(
                    (parent, view, position, id) -> {
                      DexEntry entry = found.get(position);
                      dialog.dismiss();
                      openDex(context, entry.file);
                    });
              });
        });
  }

  // ------------------------------------------------------------------ step 2: load one dex

  private void openDex(Context context, File dex) {
    AtomicBoolean cancelled = new AtomicBoolean(false);

    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), 0);
    TextView message =
        Ui.text(
            context,
            L.t("Loading ", "در حال بارگذاری ") + dex.getName() + " ...\n"
                + L.t(
                    "Big files can take a minute.", "فایل‌های بزرگ ممکن است یک دقیقه طول بکشند."),
            14,
            Ui.onSurface(),
            false);
    ProgressBar spinner = new ProgressBar(context);
    spinner.setIndeterminate(true);
    M3Theme.progressBar(spinner);
    box.addView(message);
    box.addView(spinner);

    AlertDialog loading =
        new DialogCompat(context)
            .setTitle("JADX")
            .setView(box)
            .setCancelable(true)
            .setNegativeButton(L.t("Cancel", "لغو"), null)
            .create();
    loading.setOnDismissListener(d -> cancelled.set(true));
    loading.show();

    File outDir = chooseOutDir(dex);
    File workDir = new File(pluginContext.getCacheDir(), "jadx-work/" + System.nanoTime());

    runOnWorker(
        () -> {
          closeCurrent();

          JadxSession opened = null;
          try {
            JadxEngine engine = engines.get();
            opened = engine.open(dex, outDir, workDir, settings.toOptions());
          } catch (Throwable error) {
            plugin.getLogger().error("jadx: cannot load " + dex, error);
            deleteRecursively(workDir);
            main.post(
                () -> {
                  if (loading.isShowing()) {
                    loading.dismiss();
                  }
                  showError(context, L.t("Cannot load dex", "بارگذاری dex ناموفق بود"), error);
                });
            return;
          }

          JadxSession session = opened;
          if (cancelled.get() || shutdown) {
            session.close();
            deleteRecursively(workDir);
            return;
          }
          synchronized (this) {
            current = session;
            currentWork = workDir;
          }
          main.post(
              () -> {
                if (loading.isShowing()) {
                  loading.dismiss();
                }
                showClasses(context, session, dex, outDir);
              });
        });
  }

  // ------------------------------------------------------------------ step 3: class browser

  private void showClasses(Context context, JadxSession session, File dex, File outDir) {
    List<String> all = session.classNames();
    String[] lower = new String[all.size()];
    for (int i = 0; i < lower.length; i++) {
      lower[i] = all.get(i).toLowerCase(Locale.ROOT);
    }
    List<String> shown = new ArrayList<>(all);

    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0);

    TextView info =
        Ui.text(
            context,
            L.t(all.size() + " classes", all.size() + " کلاس") + "  -  " + outDir.getAbsolutePath(),
            12,
            Ui.onSurfaceVariant(),
            false);
    info.setTextDirection(View.TEXT_DIRECTION_LTR);

    EditText search = new EditText(context);
    search.setSingleLine(true);
    search.setHint(L.t("Search classes", "جستجوی کلاس"));
    search.setTextColor(Ui.onSurface());
    search.setHintTextColor(Ui.onSurfaceVariant());
    search.setTextDirection(View.TEXT_DIRECTION_LTR);
    M3Theme.editText(search);
    search.setBackground(Ui.box(context, Ui.surface(), Ui.outline(), 12));
    search.setPadding(
        Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10));

    ListView list = new ListView(context);
    list.setDividerHeight(0);
    ClassAdapter adapter = new ClassAdapter(context, shown);
    list.setAdapter(adapter);

    TextView decompileAllButton =
        Ui.button(
            context,
            L.t("Decompile all", "دیکامپایل همه"),
            v -> decompileAll(context, session, outDir));

    LinearLayout.LayoutParams searchParams =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    searchParams.topMargin = Ui.dp(context, 8);
    searchParams.bottomMargin = Ui.dp(context, 8);

    LinearLayout.LayoutParams buttonParams =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    buttonParams.topMargin = Ui.dp(context, 12);

    box.addView(info);
    box.addView(search, searchParams);
    box.addView(
        list,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 340)));
    box.addView(decompileAllButton, buttonParams);

    search.addTextChangedListener(
        new SimpleWatcher(
            text -> {
              String query = text.trim().toLowerCase(Locale.ROOT);
              shown.clear();
              if (query.isEmpty()) {
                shown.addAll(all);
              } else {
                for (int i = 0; i < lower.length; i++) {
                  if (lower[i].contains(query)) {
                    shown.add(all.get(i));
                  }
                }
              }
              adapter.setQuery(query);
              adapter.notifyDataSetChanged();
            }));

    list.setOnItemClickListener(
        (parent, view, position, id) -> decompileOne(context, session, shown.get(position)));

    AlertDialog dialog =
        new DialogCompat(context)
            .setTitle(dex.getName())
            .setView(box)
            .setCancelable(true)
            .setNegativeButton(L.t("Close", "بستن"), null)
            .create();
    dialog.setOnDismissListener(d -> runOnWorker(() -> closeIfCurrent(session)));
    dialog.show();
  }

  // ------------------------------------------------------------------ step 4a: one class

  private void decompileOne(Context context, JadxSession session, String className) {
    toast(L.t("Decompiling ", "در حال دیکامپایل ") + simpleName(className));
    runOnWorker(
        () -> {
          try {
            String code = session.decompileClass(className);
            File out = new File(session.sourcesDir(), session.sourcePathOf(className));
            File parent = out.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
              throw new IOException("Cannot create " + parent);
            }
            Files.write(out.toPath(), code.getBytes(StandardCharsets.UTF_8));
            main.post(
                () -> {
                  refreshFiles();
                  showCode(context, className, code, out);
                });
          } catch (Throwable error) {
            plugin.getLogger().error("jadx: cannot decompile " + className, error);
            main.post(() -> showError(context, simpleName(className), error));
          }
        });
  }

  private void showCode(Context context, String className, String code, File file) {
    String text = code;
    if (code.length() > CODE_PREVIEW_LIMIT) {
      text =
          code.substring(0, CODE_PREVIEW_LIMIT)
              + "\n\n// ... "
              + L.t(
                  "preview cut, open in editor for the full source",
                  "پیش‌نمایش بریده شد؛ برای متن کامل در ادیتور باز کن");
    }

    TextView view =
        Ui.text(context, JavaHighlighter.fromTheme().highlight(text), 11, Ui.onSurface(), false);
    view.setTypeface(Typeface.MONOSPACE);
    view.setTextIsSelectable(true);
    view.setTextDirection(View.TEXT_DIRECTION_LTR);
    view.setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8));

    ScrollView scroll = new ScrollView(context);
    scroll.addView(view);

    new DialogCompat(context)
        .setTitle(simpleName(className))
        .setView(scroll)
        .setCancelable(true)
        .setPositiveButton(L.t("Open in editor", "باز کردن در ادیتور"), (d, which) -> openInEditor(file))
        .setNegativeButton(L.t("Close", "بستن"), null)
        .create()
        .show();
  }

  private void openInEditor(File file) {
    EditorHost editor = plugin.getServices().get(IdeHostServices.EDITOR_HOST);
    if (editor != null) {
      editor.openFile(file);
      return;
    }
    FileManagerHost files = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
    if (files != null) {
      files.openFile(file);
      return;
    }
    toast(L.t("Saved: ", "ذخیره شد: ") + file.getAbsolutePath());
  }

  // ------------------------------------------------------------------ step 4b: whole dex

  private void decompileAll(Context context, JadxSession session, File outDir) {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), 0);

    TextView percent =
        Ui.text(context, L.t("Starting...", "در حال شروع ..."), 14, Ui.onSurface(), false);
    ProgressBar bar =
        new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    bar.setIndeterminate(false);
    bar.setMax(100);
    M3Theme.progressBar(bar);
    box.addView(percent);
    box.addView(bar);

    AlertDialog dialog =
        new DialogCompat(context)
            .setTitle(L.t("Decompile all", "دیکامپایل همه"))
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(L.t("Cancel", "لغو"), (d, which) -> helper.execute(session::cancel))
            .create();
    dialog.show();

    runOnWorker(
        () -> {
          try {
            SaveResult result =
                session.saveAll(
                    (done, total) ->
                        main.post(
                            () -> {
                              int value = total > 0 ? Math.min(100, done * 100 / total) : 0;
                              bar.setProgress(value);
                              percent.setText(value + " %");
                            }));
            main.post(
                () -> {
                  if (dialog.isShowing()) {
                    dialog.dismiss();
                  }
                  refreshFiles();
                  showResult(context, result, outDir);
                });
          } catch (Throwable error) {
            plugin.getLogger().error("jadx: decompile all failed", error);
            main.post(
                () -> {
                  if (dialog.isShowing()) {
                    dialog.dismiss();
                  }
                  showError(context, L.t("Decompile failed", "دیکامپایل ناموفق بود"), error);
                });
          }
        });
  }

  private void showResult(Context context, SaveResult result, File outDir) {
    String title =
        result.cancelled ? L.t("Cancelled", "لغو شد") : L.t("Done", "تمام شد");
    String message =
        L.t("Classes: ", "کلاس‌ها: ") + result.classes
            + "\n" + L.t("Errors: ", "خطاها: ") + result.errors
            + "\n" + L.t("Warnings: ", "هشدارها: ") + result.warnings
            + "\n" + L.t("Time: ", "زمان: ") + (result.millis / 1000) + " s"
            + "\n\n" + new File(outDir, "sources").getAbsolutePath();
    new DialogCompat(context)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(true)
        .setPositiveButton("OK", null)
        .create()
        .show();
  }

  // ------------------------------------------------------------------ helpers

  private void showError(Context context, String title, Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String detail = root.getClass().getSimpleName();
    if (root.getMessage() != null && !root.getMessage().isEmpty()) {
      detail += ": " + root.getMessage();
    }
    if (error instanceof OutOfMemoryError) {
      detail +=
          "\n\n"
              + L.t(
                  "Not enough memory. Turn on Fallback mode in the plugin settings.",
                  "حافظه کافی نیست. حالت Fallback را در تنظیمات پلاگین روشن کن.");
    }
    new DialogCompat(context)
        .setTitle(title)
        .setMessage(detail)
        .setCancelable(true)
        .setPositiveButton("OK", null)
        .create()
        .show();
  }

  private void toast(String message) {
    UiFeedbackHost feedback = plugin.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message);
    }
  }

  private void refreshFiles() {
    FileManagerHost files = plugin.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
    if (files != null) {
      files.refresh();
    }
  }

  /** Output goes next to the dex ({@code classes_jadx/}); falls back to the plugin's own storage. */
  private File chooseOutDir(File dex) {
    String name = dex.getName();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    File parent = dex.getParentFile();
    if (parent != null && parent.canWrite()) {
      return new File(parent, base + "_jadx");
    }
    File external = pluginContext.getExternalFilesDir(null);
    File root = external != null ? external : pluginContext.getFilesDir();
    return new File(root, "jadx-output/" + base);
  }

  /** Submits to the worker; ignored after the plugin was deactivated. */
  private void runOnWorker(Runnable task) {
    try {
      worker.execute(task);
    } catch (RejectedExecutionException ignored) {
      // plugin is shutting down
    }
  }

  private synchronized void closeCurrent() {
    JadxSession session = current;
    File work = currentWork;
    current = null;
    currentWork = null;
    if (session != null) {
      try {
        session.close();
      } catch (Throwable ignored) {
        // best effort
      }
    }
    if (work != null) {
      deleteRecursively(work);
    }
  }

  private void closeIfCurrent(JadxSession session) {
    boolean isCurrent;
    synchronized (this) {
      isCurrent = current == session;
    }
    if (isCurrent) {
      closeCurrent();
    }
  }

  private static void deleteRecursively(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }

  private static String simpleName(String className) {
    int dot = className.lastIndexOf('.');
    return dot >= 0 ? className.substring(dot + 1) : className;
  }

  private static ThreadFactory named(String name, long stackSize) {
    return runnable -> {
      Thread thread = new Thread(null, runnable, name, stackSize);
      thread.setDaemon(true);
      return thread;
    };
  }

  // ------------------------------------------------------------------ adapters

  private static final class DexAdapter extends BaseAdapter {
    private final Context context;
    private final File root;
    private final List<DexEntry> items;

    DexAdapter(Context context, File root, List<DexEntry> items) {
      this.context = context;
      this.root = root;
      this.items = items;
    }

    @Override
    public int getCount() {
      return items.size();
    }

    @Override
    public Object getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View row = convertView != null ? convertView : Ui.twoLineRow(context);
      DexEntry entry = items.get(position);
      Ui.bindRow(row, entry.file.getName() + "   (" + entry.sizeText() + ")", entry.relativeTo(root));
      return row;
    }
  }

  private static final class ClassAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> items;
    private String query = "";

    ClassAdapter(Context context, List<String> items) {
      this.context = context;
      this.items = items;
    }

    void setQuery(String query) {
      this.query = query == null ? "" : query;
    }

    @Override
    public int getCount() {
      return items.size();
    }

    @Override
    public Object getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View row = convertView != null ? convertView : Ui.twoLineRow(context);
      String name = items.get(position);
      int dot = name.lastIndexOf('.');
      String simple = dot >= 0 ? name.substring(dot + 1) : name;
      String pkg = dot >= 0 ? name.substring(0, dot) : "";
      int color = Ui.primary();
      Ui.bindRow(
          row,
          Ui.highlightMatches(simple, query, color),
          Ui.highlightMatches(pkg, query, color));
      return row;
    }
  }

  private interface TextConsumer {
    void accept(String text);
  }

  private static final class SimpleWatcher implements TextWatcher {
    private final TextConsumer consumer;

    SimpleWatcher(TextConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      consumer.accept(s.toString());
    }
  }
}
