package ir.hanzodev1375.csv;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/** Native table viewer for CSV/TSV: sticky header, sort, search, header toggle, delimiter picker. */
final class CsvViewerView extends LinearLayout {

  private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
  private static final int SAMPLE_ROWS = 300;
  private static final String[] DELIM_LABELS = {"Auto", ",", ";", "Tab", "|"};
  private static final char[] DELIM_VALUES = {0, ',', ';', '\t', '|'};

  private final Context ctx;
  private final EditorHost editorHost;
  private final PluginLogger logger;
  private final boolean night;
  private final boolean fa = "fa".equals(Locale.getDefault().getLanguage());
  private final float dp;
  private final ThreadPoolExecutor exec = newExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  // palette
  private final int cBg;
  private final int cAlt;
  private final int cHeader;
  private final int cText;
  private final int cMuted;
  private final int cLine;
  private final int cAccent;

  private final TextView info;
  private final TextView delimBtn;
  private final EditText search;
  private final CheckBox headerBox;
  private final HorizontalScrollView hscroll;
  private final LinearLayout tableBox;
  private final LinearLayout headerRow;
  private final ListView list;
  private final TextView empty;
  private final RowAdapter adapter = new RowAdapter();

  private String sourceText = "";
  private String sourceName = "";
  private int delimChoice = 0;
  private boolean hasHeader = true;
  private char usedDelim = ',';
  private boolean truncated;

  private List<String[]> data = Collections.emptyList();
  private String[] headerNames = new String[0];
  private int cols;
  private int[] colWidthPx = new int[0];
  private int numWidthPx;
  private int[] order = new int[0];
  private int sortCol = -1;
  private boolean sortAsc = true;
  private String query = "";
  private int generation;

  CsvViewerView(Context context, EditorHost editorHost, PluginLogger logger) {
    super(context);
    this.ctx = context;
    this.editorHost = editorHost;
    this.logger = logger;
    this.dp = context.getResources().getDisplayMetrics().density;
    int ui = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    this.night = ui == Configuration.UI_MODE_NIGHT_YES;
    cBg = night ? 0xFF1E1E1E : 0xFFFFFFFF;
    cAlt = night ? 0xFF252526 : 0xFFF5F7FA;
    cHeader = night ? 0xFF303033 : 0xFFE3E8EF;
    cText = night ? 0xFFDDDDDD : 0xFF1B1F24;
    cMuted = night ? 0xFF9AA0A6 : 0xFF5F6368;
    cLine = night ? 0xFF3C3C3C : 0xFFD0D7DE;
    cAccent = night ? 0xFF4FC3F7 : 0xFF0B6BCB;

    setOrientation(VERTICAL);
    setBackgroundColor(cBg);

    // ---- toolbar ----
    LinearLayout bar1 = new LinearLayout(ctx);
    bar1.setOrientation(HORIZONTAL);
    bar1.setGravity(Gravity.CENTER_VERTICAL);
    bar1.setPadding(px(10), px(8), px(10), px(4));

    info = new TextView(ctx);
    info.setTextColor(cMuted);
    info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    info.setSingleLine(true);
    info.setEllipsize(TextUtils.TruncateAt.END);
    bar1.addView(info, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    delimBtn = chip("Auto");
    delimBtn.setOnClickListener(v -> cycleDelimiter());
    bar1.addView(delimBtn);

    TextView reload = chip("\u27F3");
    reload.setOnClickListener(v -> reload());
    LayoutParams rlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rlp.leftMargin = px(6);
    bar1.addView(reload, rlp);
    addView(bar1);

    LinearLayout bar2 = new LinearLayout(ctx);
    bar2.setOrientation(HORIZONTAL);
    bar2.setGravity(Gravity.CENTER_VERTICAL);
    bar2.setPadding(px(10), 0, px(10), px(6));

    search = new EditText(ctx);
    search.setHint(t("Search…", "جستجو…"));
    search.setHintTextColor(cMuted);
    search.setTextColor(cText);
    search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    search.setSingleLine(true);
    search.setPadding(px(10), px(6), px(10), px(6));
    search.setBackground(round(cAlt, cLine, 8));
    search.addTextChangedListener(
        new TextWatcher() {
          @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
          @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
          @Override
          public void afterTextChanged(Editable s) {
            query = s.toString();
            refreshOrder();
          }
        });
    bar2.addView(search, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    headerBox = new CheckBox(ctx);
    headerBox.setText(t("Header", "سرستون"));
    headerBox.setTextColor(cText);
    headerBox.setChecked(true);
    headerBox.setOnCheckedChangeListener(
        (b, checked) -> {
          hasHeader = checked;
          rebuild();
        });
    bar2.addView(headerBox);
    addView(bar2);

    // ---- table ----
    hscroll = new HorizontalScrollView(ctx);
    hscroll.setFillViewport(true);
    hscroll.setHorizontalScrollBarEnabled(true);

    tableBox = new LinearLayout(ctx);
    tableBox.setOrientation(VERTICAL);

    headerRow = new LinearLayout(ctx);
    headerRow.setOrientation(HORIZONTAL);
    headerRow.setBackgroundColor(cHeader);
    headerRow.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    headerRow.setDividerDrawable(divider());
    tableBox.addView(headerRow);

    list = new ListView(ctx);
    list.setDivider(null);
    list.setDividerHeight(0);
    list.setFastScrollEnabled(true);
    list.setAdapter(adapter);
    tableBox.addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    hscroll.addView(tableBox, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

    empty = new TextView(ctx);
    empty.setTextColor(cMuted);
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(px(24), px(24), px(24), px(24));
    empty.setVisibility(GONE);

    addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    addView(hscroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
  }

  private static ThreadPoolExecutor newExecutor() {
    ThreadPoolExecutor e =
        new ThreadPoolExecutor(
            1, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> {
              Thread t = new Thread(r, "csv-viewer");
              t.setDaemon(true);
              return t;
            });
    e.allowCoreThreadTimeOut(true);
    return e;
  }

  // ------------------------------------------------------------------ loading

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    reload();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    generation++;
  }

  /** Re-reads the open file (editor buffer first, so unsaved edits show up). */
  void reload() {
    File file = null;
    String text = null;
    try {
      file = editorHost.getOpenFile();
      String buffer = editorHost.getEditorText();
      if (buffer != null && !buffer.isEmpty()) {
        text = buffer;
      }
    } catch (RuntimeException e) {
      logger.warn("csv: cannot read editor state", e);
    }
    if (file == null) {
      showMessage(t("No file is open.", "هیچ فایلی باز نیست."));
      return;
    }
    sourceName = file.getName();
    if (text == null) {
      final File f = file;
      final int gen = ++generation;
      exec.execute(
          () -> {
            String read = readFile(f);
            main.post(() -> {
              if (gen == generation) {
                applyText(read);
              }
            });
          });
      return;
    }
    applyText(text);
  }

  private void applyText(String text) {
    sourceText = text == null ? "" : text;
    if (sourceText.isEmpty()) {
      showMessage(t("The file is empty.", "فایل خالی است."));
      return;
    }
    rebuild();
  }

  private String readFile(File f) {
    if (f == null || !f.isFile()) {
      return "";
    }
    if (f.length() > MAX_FILE_BYTES) {
      logger.warn("csv: file too large for the viewer: " + f.length());
      return "";
    }
    try (InputStream in = new FileInputStream(f)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(f.length(), 16));
      byte[] buf = new byte[16 * 1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.warn("csv: cannot read " + f, e);
      return "";
    }
  }

  private void showMessage(String message) {
    generation++;
    empty.setText(message);
    empty.setVisibility(VISIBLE);
    hscroll.setVisibility(GONE);
    info.setText(sourceName);
  }

  // ------------------------------------------------------------------ parsing

  private void rebuild() {
    if (sourceText.isEmpty()) {
      return;
    }
    final int gen = ++generation;
    final String text = sourceText;
    final boolean header = hasHeader;
    final int choice = delimChoice;
    info.setText(t("Parsing…", "در حال پردازش…"));
    exec.execute(
        () -> {
          char delim = choice == 0 ? CsvParser.detectDelimiter(text) : DELIM_VALUES[choice];
          CsvParser.Result result = CsvParser.parse(text, delim);
          List<String[]> all = result.rows;
          int ncols = Math.max(result.maxCols, 1);
          String[] names = new String[ncols];
          List<String[]> body;
          if (header && !all.isEmpty()) {
            String[] first = all.get(0);
            for (int c = 0; c < ncols; c++) {
              String h = c < first.length ? first[c] : "";
              names[c] = h.isEmpty() ? columnLetter(c) : h;
            }
            body = all.subList(1, all.size());
          } else {
            for (int c = 0; c < ncols; c++) {
              names[c] = columnLetter(c);
            }
            body = all;
          }
          int[] widths = measure(names, body, ncols);
          main.post(
              () -> {
                if (gen != generation) {
                  return;
                }
                usedDelim = delim;
                truncated = result.truncated;
                order = new int[0];
                data = body;
                headerNames = names;
                cols = ncols;
                colWidthPx = widths;
                sortCol = -1;
                sortAsc = true;
                buildHeader();
                refreshOrder();
                empty.setVisibility(GONE);
                hscroll.setVisibility(VISIBLE);
                updateInfo();
              });
        });
  }

  private int[] measure(String[] names, List<String[]> body, int ncols) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(13 * ctx.getResources().getDisplayMetrics().scaledDensity);
    float[] max = new float[ncols];
    for (int c = 0; c < ncols; c++) {
      max[c] = paint.measureText(names[c]) + px(18); // room for the sort arrow
    }
    int limit = Math.min(body.size(), SAMPLE_ROWS);
    for (int r = 0; r < limit; r++) {
      String[] row = body.get(r);
      for (int c = 0; c < row.length && c < ncols; c++) {
        String cell = row[c];
        if (cell.length() > 80) {
          cell = cell.substring(0, 80);
        }
        max[c] = Math.max(max[c], paint.measureText(cell));
      }
    }
    int[] widths = new int[ncols];
    for (int c = 0; c < ncols; c++) {
      widths[c] = (int) Math.max(px(56), Math.min(px(260), max[c] + px(20)));
    }
    return widths;
  }

  // ------------------------------------------------------------------ table

  private void buildHeader() {
    headerRow.removeAllViews();
    numWidthPx = px(Math.max(40, 12 + 9 * String.valueOf(Math.max(data.size(), 1)).length()));

    TextView num = headerCell("#", numWidthPx, false);
    headerRow.addView(num);
    int total = numWidthPx;
    for (int c = 0; c < cols; c++) {
      final int col = c;
      String label = headerNames[c] + (c == sortCol ? (sortAsc ? "  \u25B2" : "  \u25BC") : "");
      TextView tv = headerCell(label, colWidthPx[c], true);
      tv.setOnClickListener(v -> sortBy(col));
      headerRow.addView(tv);
      total += colWidthPx[c] + 1;
    }
    total += 1;
    headerRow.setLayoutParams(new LayoutParams(total, ViewGroup.LayoutParams.WRAP_CONTENT));
    list.setLayoutParams(new LayoutParams(total, 0, 1f));
    list.setAdapter(adapter); // also clears recycled rows built for the old column set
  }

  private void sortBy(int col) {
    if (sortCol == col) {
      sortAsc = !sortAsc;
    } else {
      sortCol = col;
      sortAsc = true;
    }
    buildHeader();
    refreshOrder();
  }

  /** Recomputes the visible row order (filter + sort) off the UI thread. */
  private void refreshOrder() {
    final int gen = generation;
    final List<String[]> rows = data;
    final String q = query.trim().toLowerCase(Locale.ROOT);
    final int col = sortCol;
    final boolean asc = sortAsc;
    exec.execute(
        () -> {
          int[] idx = new int[rows.size()];
          int n = 0;
          for (int i = 0; i < rows.size(); i++) {
            if (q.isEmpty() || rowMatches(rows.get(i), q)) {
              idx[n++] = i;
            }
          }
          Integer[] boxed = new Integer[n];
          for (int i = 0; i < n; i++) {
            boxed[i] = idx[i];
          }
          if (col >= 0) {
            Arrays.sort(boxed, (a, b) -> {
              int r = compareCells(cell(rows.get(a), col), cell(rows.get(b), col));
              return asc ? r : -r;
            });
          }
          int[] result = new int[n];
          for (int i = 0; i < n; i++) {
            result[i] = boxed[i];
          }
          main.post(
              () -> {
                if (gen != generation) {
                  return;
                }
                order = result;
                adapter.notifyDataSetChanged();
                updateInfo();
              });
        });
  }

  private static boolean rowMatches(String[] row, String q) {
    for (String cell : row) {
      if (cell.toLowerCase(Locale.ROOT).contains(q)) {
        return true;
      }
    }
    return false;
  }

  private static String cell(String[] row, int col) {
    return col < row.length ? row[col] : "";
  }

  private static int compareCells(String a, String b) {
    Double da = parseNumber(a);
    Double db = parseNumber(b);
    if (da != null && db != null) {
      return Double.compare(da, db);
    }
    if (da != null) {
      return -1;
    }
    if (db != null) {
      return 1;
    }
    return a.compareToIgnoreCase(b);
  }

  private static Double parseNumber(String s) {
    if (s == null || s.isEmpty() || s.length() > 40) {
      return null;
    }
    try {
      return Double.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void updateInfo() {
    String delim = usedDelim == '\t' ? "Tab" : String.valueOf(usedDelim);
    delimBtn.setText(delimChoice == 0 ? "Auto (" + delim + ")" : DELIM_LABELS[delimChoice]);
    StringBuilder sb = new StringBuilder(sourceName);
    sb.append("  \u00B7  ");
    if (order.length != data.size()) {
      sb.append(order.length).append(" / ");
    }
    sb.append(data.size()).append(t(" rows × ", " ردیف × ")).append(cols).append(t(" cols", " ستون"));
    if (truncated) {
      sb.append(t("  (truncated)", "  (بریده‌شده)"));
    }
    info.setText(sb.toString());
  }

  private void cycleDelimiter() {
    delimChoice = (delimChoice + 1) % DELIM_LABELS.length;
    delimBtn.setText(DELIM_LABELS[delimChoice]);
    rebuild();
  }

  private void showCell(int position, int col) {
    if (position < 0 || position >= order.length) {
      return;
    }
    String[] row = data.get(order[position]);
    final String value = cell(row, col);
    TextView body = new TextView(ctx);
    body.setText(value);
    body.setTextIsSelectable(true);
    body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
    body.setPadding(px(20), px(12), px(20), px(4));
    ScrollView scroll = new ScrollView(ctx);
    scroll.addView(body);
    try {
      new AlertDialog.Builder(ctx)
          .setTitle(headerNames[col] + "  \u00B7  #" + (order[position] + 1))
          .setView(scroll)
          .setPositiveButton(t("Copy", "کپی"), (d, w) -> copy(value))
          .setNegativeButton(t("Close", "بستن"), null)
          .show();
    } catch (RuntimeException e) {
      copy(value);
    }
  }

  private void copy(String value) {
    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null) {
      cm.setPrimaryClip(ClipData.newPlainText("csv", value));
      Toast.makeText(ctx, t("Copied", "کپی شد"), Toast.LENGTH_SHORT).show();
    }
  }

  // ------------------------------------------------------------------ adapter

  private final class RowAdapter extends BaseAdapter {
    @Override public int getCount() { return order.length; }
    @Override public Object getItem(int position) { return null; }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LinearLayout row = (LinearLayout) convertView;
      if (row == null) {
        row = createRow();
      }
      int dataIndex = order[position];
      String[] values = data.get(dataIndex);
      row.setTag(position);
      row.setBackgroundColor(position % 2 == 0 ? cBg : cAlt);
      ((TextView) row.getChildAt(0)).setText(String.valueOf(dataIndex + 1));
      for (int c = 0; c < cols; c++) {
        ((TextView) row.getChildAt(c + 1)).setText(cell(values, c));
      }
      return row;
    }
  }

  private LinearLayout createRow() {
    final LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(HORIZONTAL);
    row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    row.setDividerDrawable(divider());
    TextView num = bodyCell(numWidthPx);
    num.setTextColor(cMuted);
    num.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    row.addView(num);
    for (int c = 0; c < cols; c++) {
      final int col = c;
      TextView tv = bodyCell(colWidthPx[c]);
      tv.setOnClickListener(v -> showCell((Integer) row.getTag(), col));
      row.addView(tv);
    }
    row.setLayoutParams(new ListView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return row;
  }

  // ------------------------------------------------------------------ view helpers

  private TextView bodyCell(int widthPx) {
    TextView tv = new TextView(ctx);
    tv.setTextColor(cText);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    tv.setSingleLine(true);
    tv.setEllipsize(TextUtils.TruncateAt.END);
    tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
    tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    tv.setPadding(px(10), px(9), px(10), px(9));
    tv.setLayoutParams(new LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
    return tv;
  }

  private TextView headerCell(String text, int widthPx, boolean sortable) {
    TextView tv = bodyCell(widthPx);
    tv.setText(text);
    tv.setTypeface(Typeface.DEFAULT_BOLD);
    tv.setTextColor(sortable ? cAccent : cMuted);
    if (!sortable) {
      tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    }
    return tv;
  }

  private TextView chip(String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextColor(cAccent);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    tv.setPadding(px(10), px(5), px(10), px(5));
    tv.setBackground(round(cAlt, cLine, 14));
    return tv;
  }

  private GradientDrawable round(int fill, int stroke, int radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setStroke(1, stroke);
    d.setCornerRadius(px(radiusDp));
    return d;
  }

  private GradientDrawable divider() {
    GradientDrawable d = new GradientDrawable();
    d.setColor(cLine);
    d.setSize(1, 1);
    return d;
  }

  private int px(int dpValue) {
    return Math.round(dpValue * dp);
  }

  private String t(String en, String faText) {
    return fa ? faText : en;
  }

  private static String columnLetter(int index) {
    StringBuilder sb = new StringBuilder();
    int n = index;
    do {
      sb.insert(0, (char) ('A' + n % 26));
      n = n / 26 - 1;
    } while (n >= 0);
    return sb.toString();
  }
}
