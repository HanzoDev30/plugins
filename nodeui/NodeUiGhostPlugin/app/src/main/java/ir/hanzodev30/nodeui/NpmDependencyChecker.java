package ir.hanzodev30.nodeui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer;
import io.github.rosemoe.sora.lang.styling.color.EditorColor;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.base.EditorPopupWindow;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import ir.hanzodev1375.ghostide.codeeditors.colorscheme.GhostColorScheme;
import ir.hanzodev1375.ghostide.codeeditors.preview.EditorPopUp;

public final class NpmDependencyChecker {

  private static final long PROCESS_DELAY_MS = 120;
  private static final ExecutorService POOL = Executors.newCachedThreadPool();

  static final class Dep {
    final String name;
    final String version;
    final int line;
    final int nameStart;
    final int nameEnd;
    final int valueStart;
    final int valueEnd;

    Dep(String name, String version, int line, int nameStart, int nameEnd, int valueStart, int valueEnd) {
      this.name = name;
      this.version = version;
      this.line = line;
      this.nameStart = nameStart;
      this.nameEnd = nameEnd;
      this.valueStart = valueStart;
      this.valueEnd = valueEnd;
    }

    int fullEnd() {
      return valueEnd;
    }
  }

  private final CodeEditor editor;
  private final Context appContext;

  private final Map<String, String> updateCache = new HashMap<>();
  private final Set<String> checking = new HashSet<>();

  private String currentFilePath;
  private long lastProcessTime;
  private String lastCoordinates = "";
  private EditorPopupWindow activePopup;

  public NpmDependencyChecker(CodeEditor editor, Context appContext) {
    this.editor = editor;
    this.appContext = appContext;
  }

  static boolean handles(String path) {
    return path != null && path.endsWith("/package.json");
  }

  void setFilePath(String path) {
    currentFilePath = handles(path) ? path : null;
    refreshHighlights();
  }

  void attach() {
    refreshHighlights();

    editor.subscribeEvent(
        ContentChangeEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath != null) refreshHighlights();
        });

    editor.subscribeEvent(
        ScrollEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath != null) refreshHighlights();
        });

    editor.subscribeEvent(
        SelectionChangeEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath == null || event.getCause() != SelectionChangeEvent.CAUSE_TAP) return;
          long now = System.currentTimeMillis();
          if (now - lastProcessTime < PROCESS_DELAY_MS) return;
          lastProcessTime = now;
          editor.post(
              () -> {
                try {
                  handleSelection(event);
                } catch (RuntimeException e) {
                  dismissPopup();
                }
              });
        });
  }

  void release() {
    dismissPopup();
    checking.clear();
  }

  void refreshHighlights() {
    if (currentFilePath == null) {
      editor.setHighlightTexts(null);
      return;
    }

    HighlightTextContainer container = new HighlightTextContainer();
    Content text = editor.getText();
    int last = Math.min(text.getLineCount() - 1, editor.getLastVisibleLine());
    int first = Math.max(0, editor.getFirstVisibleLine());
    if (last < first) {
      editor.setHighlightTexts(null);
      return;
    }

    List<Dep> deps = new ArrayList<>(1);
    collect(text, first, last, deps);
    for (Dep dep : deps) {
      if (dep.valueStart >= dep.valueEnd) continue;
      String newest = updateCache.get(dep.name);
      if (newest != null && isUpdate(dep.version, newest)) {
        container.add(
            new HighlightTextContainer.HighlightText(
                dep.line,
                dep.nameStart,
                dep.line,
                dep.fullEnd(),
                new EditorColor(GhostColorScheme.DEPENDENCY_UPDATE_AVAILABLE_BG),
                new EditorColor(GhostColorScheme.DEPENDENCY_UPDATE_AVAILABLE)));
      }
      checkInBackground(dep.name);
    }

    editor.setHighlightTexts(container.isEmpty() ? null : container);
  }

  private void checkInBackground(String name) {
    if (!checking.add(name)) return;
    POOL.execute(
        () -> {
          String newest = null;
          try {
            NodeClient.Result result = NodeClient.lookup(appContext, name);
            if (result != null && result.isFound() && result.pkg != null && !result.pkg.version.isEmpty()) {
              newest = result.pkg.version;
            }
          } catch (Exception ignored) {
          }
          String finalNewest = newest;
          editor.post(
              () -> {
                updateCache.put(name, finalNewest == null ? "" : finalNewest);
                checking.remove(name);
                if (currentFilePath != null) refreshHighlights();
              });
        });
  }

  private void handleSelection(SelectionChangeEvent event) {
    if (editor.getCursor().isSelected()) {
      dismissPopup();
      lastCoordinates = "";
      return;
    }

    Dep dep = findAt(editor.getText(), event.getLeft().getLine(), event.getLeft().getColumn());
    if (dep == null) {
      dismissPopup();
      lastCoordinates = "";
      return;
    }

    String coordinates = dep.name;
    if (coordinates.equals(lastCoordinates) && activePopup != null && activePopup.isShowing()) return;
    lastCoordinates = coordinates;

    String cached = updateCache.get(coordinates);
    if (cached != null && !cached.isEmpty()) {
      showResult(dep, cached);
      return;
    }

    showLoading(dep);
    POOL.execute(
        () -> {
          String newest = null;
          try {
            NodeClient.Result result = NodeClient.lookup(appContext, coordinates);
            if (result != null && result.isFound() && result.pkg != null && !result.pkg.version.isEmpty()) {
              newest = result.pkg.version;
            }
          } catch (Exception ignored) {
          }
          if (newest != null && !newest.isEmpty()) {
            updateCache.put(coordinates, newest);
          }
          String finalNewest = newest;
          editor.post(() -> showResult(dep, finalNewest));
        });
  }

  // ── Manifest parsing ──────────────────────────────────────────────────────

  static void collect(Content text, int first, int last, List<Dep> out) {
    boolean section = false;
    for (int line = first; line <= last; line++) {
      String s = text.getLineString(line);
      String t = s.trim();
      if (t.startsWith("\"dependencies\"") || t.startsWith("\"devDependencies\"")) {
        section = true;
        continue;
      }
      if (section && t.equals("}")) {
        section = false;
        continue;
      }
      if (!section) continue;
      int q1 = s.indexOf('"');
      if (q1 < 0) continue;
      int q2 = s.indexOf('"', q1 + 1);
      if (q2 < 0) continue;
      int q3 = s.indexOf('"', q2 + 1);
      if (q3 < 0) continue;
      int q4 = s.indexOf('"', q3 + 1);
      if (q4 < 0) continue;
      String name = s.substring(q1 + 1, q2);
      String version = s.substring(q3 + 1, q4);
      if (name.isEmpty() || version.isEmpty()) continue;
      out.add(new Dep(name, version, line, q1, q2 + 1, q3, q4 + 1));
    }
  }

  static Dep findAt(Content text, int line, int col) {
    if (line < 0 || line >= text.getLineCount()) return null;
    List<Dep> deps = new ArrayList<>(1);
    collect(text, line, line, deps);
    for (Dep dep : deps) {
      if (col >= dep.nameStart && col < dep.fullEnd()) return dep;
    }
    return null;
  }

  static boolean isUpdate(String installed, String newest) {
    String a = clean(installed);
    String b = clean(newest);
    if (a.isEmpty() || b.isEmpty()) return false;
    if (a.equals("latest") || a.equals("*") || a.equals("workspace:*") || a.equals("any")) return false;
    int[] na = parts(a);
    int[] nb = parts(b);
    for (int i = 0; i < 3; i++) {
      if (nb[i] > na[i]) return true;
      if (nb[i] < na[i]) return false;
    }
    return false;
  }

  static String replacement(String installed, String newest) {
    if (installed.startsWith("^")) return "^" + newest;
    if (installed.startsWith("~")) return "~" + newest;
    return newest;
  }

  private static String clean(String value) {
    String v = value.trim();
    while (!v.isEmpty()) {
      char c = v.charAt(0);
      if (c == '^' || c == '~' || c == '>' || c == '<' || c == '=' || c == '=' || c == ',' || c == ' ') {
        v = v.substring(1);
      } else {
        break;
      }
    }
    int end = v.length();
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c == ' ' || c == ',' || c == '+' || c == '|' || c == '-') {
        end = i;
        break;
      }
    }
    return v.substring(0, end);
  }

  private static int[] parts(String version) {
    int[] result = new int[3];
    String[] tokens = version.split("\\.");
    for (int i = 0; i < 3 && i < tokens.length; i++) {
      try {
        result[i] = Integer.parseInt(tokens[i].trim());
      } catch (NumberFormatException e) {
        result[i] = 0;
      }
    }
    return result;
  }

  // ── Popup ─────────────────────────────────────────────────────────────────

  private void showLoading(Dep dep) {
    dismissPopup();
    LinearLayout root = makeRoot();
    root.addView(titleLine("Checking npm…"));
    root.addView(infoLine(dep.name));
    activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
  }

  private void showResult(Dep dep, String newest) {
    if (!dep.name.equals(lastCoordinates)) return;
    dismissPopup();

    String current = dep.version;
    LinearLayout root = makeRoot();

    if (newest == null || newest.isEmpty()) {
      root.addView(titleLine("No release found"));
      root.addView(infoLine("Current: " + current));
      activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
      return;
    }

    if (isUpdate(current, newest)) {
      root.addView(titleLine("Current: " + current));
      root.addView(infoLine("Newest: " + newest));
      root.addView(buildUpdateButton(dep, current, newest));
    } else {
      root.addView(titleLine("Up to date"));
      root.addView(infoLine("Current: " + current));
    }
    activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
  }

  private View buildUpdateButton(Dep dep, String current, String newest) {
    int accent = editor.getColorScheme().getColor(GhostColorScheme.DEPENDENCY_UPDATE_AVAILABLE);
    if (accent == 0) {
      accent = editor.getColorScheme().getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION);
    }

    Button button = new Button(editor.getContext());
    button.setText("Update to " + newest);
    button.setTextColor(contrastText(accent));
    button.setAllCaps(false);
    GradientDrawable background = new GradientDrawable();
    background.setCornerRadius(dp(12));
    background.setColor(accent);
    button.setBackground(background);

    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(10);
    button.setLayoutParams(params);
    button.setOnClickListener(v -> applyUpdate(dep, replacement(current, newest)));
    return button;
  }

  private void applyUpdate(Dep dep, String replacement) {
    int line = dep.line;
    try {
      int startOffset = editor.getText().getIndexer().getCharIndex(line, dep.valueStart);
      int endOffset = editor.getText().getIndexer().getCharIndex(line, dep.valueEnd);

      editor.getText().beginBatchEdit();
      try {
        editor.getText().replace(startOffset, endOffset, replacement);
      } finally {
        editor.getText().endBatchEdit();
      }

      editor.setSelection(line, dep.valueStart + replacement.length());
      editor.invalidate();
    } catch (RuntimeException ignored) {
    }
    dismissPopup();
    lastCoordinates = "";
  }

  private void dismissPopup() {
    if (activePopup != null) {
      try {
        activePopup.dismiss();
      } catch (RuntimeException ignored) {
      }
      activePopup = null;
    }
  }

  private LinearLayout makeRoot() {
    LinearLayout root = new LinearLayout(editor.getContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(14), dp(12), dp(14), dp(12));
    root.setGravity(Gravity.CENTER_HORIZONTAL);
    return root;
  }

  private TextView titleLine(String text) {
    TextView view = new TextView(editor.getContext());
    view.setGravity(Gravity.CENTER);
    view.setTextSize(13);
    view.setTypeface(Typeface.DEFAULT_BOLD);
    view.setTextColor(editor.getColorScheme().getColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY));
    view.setText(text);
    return view;
  }

  private TextView infoLine(String text) {
    TextView view = new TextView(editor.getContext());
    view.setGravity(Gravity.CENTER);
    view.setTextSize(12);
    view.setPadding(0, dp(4), 0, 0);
    view.setTextColor(editor.getColorScheme().getColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY));
    view.setText(text);
    return view;
  }

  private static int contrastText(int background) {
    float luminance =
        (0.299f * Color.red(background)
                + 0.587f * Color.green(background)
                + 0.114f * Color.blue(background))
            / 255f;
    return luminance > 0.5f ? Color.BLACK : Color.WHITE;
  }

  private int dp(int value) {
    return (int) (value * editor.getContext().getResources().getDisplayMetrics().density);
  }
}