package ir.ghostide.composerlsp.checker;

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
import ir.hanzodev1375.ghostide.codeeditors.dependencychecker.DependencyMatch;
import ir.hanzodev1375.ghostide.codeeditors.preview.EditorPopUp;

/**
 * Highlights Composer dependencies in a {@code composer.json} whose newest Packagist release is not
 * covered by the declared constraint, using the exact same colours as the IDE's built-in Gradle /
 * version-catalog checkers.
 *
 * <p>It is a sibling of the host's {@code DependencyCheckerIde}, not a subclass: that class looks
 * coordinates up in Maven, while Composer packages live on Packagist and are matched with Composer
 * constraint semantics. Only {@code composer.json} is claimed — every other {@code .json} file,
 * including {@code composer.lock}, keeps its plain JSON language server and is left untouched.
 *
 * <p>Highlights are only painted once a real newer release has been confirmed by the registry, and
 * tapping such a token pops up the current/newest pair with a button that rewrites the constraint
 * in place.
 */
public final class ComposerDependencyChecker {

  private static final long PROCESS_DELAY_MS = 120;

  private final CodeEditor editor;

  /** Newest known Packagist release keyed by {@code vendor:name}. */
  private final Map<String, String> updateCache = new HashMap<>();

  private final Set<String> checking = new HashSet<>();

  /** Path of the file this instance currently watches; only composer.json is checked. */
  private String currentFilePath;

  private long lastProcessTime;
  private String lastCoordinates = "";
  private EditorPopupWindow activePopup;

  public ComposerDependencyChecker(CodeEditor editor) {
    this.editor = editor;
  }

  /** True when this checker is responsible for the given file. */
  public static boolean handles(String path) {
    return ComposerManifestParser.handles(path);
  }

  /** Points the checker at a file and repaints; a path it does not handle clears the highlights. */
  public void setFilePath(String path) {
    currentFilePath = handles(path) ? path : null;
    refreshHighlights();
  }

  /** Forgets cached registry answers so the next refresh asks Packagist again. */
  public void resetUpdateCache() {
    updateCache.clear();
  }

  /** Subscribes to the editor events that can change what should be highlighted. */
  public void attach() {
    refreshHighlights();

    editor.subscribeEvent(
        ContentChangeEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath != null) {
            refreshHighlights();
          }
        });

    editor.subscribeEvent(
        ScrollEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath != null) {
            refreshHighlights();
          }
        });

    editor.subscribeEvent(
        SelectionChangeEvent.class,
        (event, unsubscribe) -> {
          if (currentFilePath == null || event.getCause() != SelectionChangeEvent.CAUSE_TAP) {
            return;
          }
          long now = System.currentTimeMillis();
          if (now - lastProcessTime < PROCESS_DELAY_MS) {
            return;
          }
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

  /** Detaches the popup; the editor itself is owned by the host. */
  public void release() {
    dismissPopup();
    checking.clear();
  }

  /**
   * Repaints the update highlights for every visible line. Lines whose newest release is not yet
   * known are registered for a background lookup; nothing is painted before the registry confirms a
   * newer version, so a failed lookup simply leaves the file untouched.
   */
  public void refreshHighlights() {
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
    boolean[] dependencyLines = ComposerManifestParser.dependencyLines(text, first, last);

    for (int line = first; line <= last; line++) {
      if (!dependencyLines[line - first]) {
        continue;
      }
      List<DependencyMatch> dependencies = new ArrayList<>(1);
      ComposerManifestParser.collectLine(text.getLineString(line), line, dependencies);
      for (DependencyMatch dependency : dependencies) {
        if (dependency.versionStart() >= dependency.versionEnd()) {
          continue;
        }
        String coordinates = dependency.coordinates();
        String newest = updateCache.get(coordinates);
        if (newest != null && ComposerConstraint.isUpdateAvailable(dependency.version(), newest)) {
          container.add(
              new HighlightTextContainer.HighlightText(
                  line,
                  dependency.fullStart(),
                  line,
                  dependency.fullEnd(),
                  new EditorColor(GhostColorScheme.DEPENDENCY_UPDATE_AVAILABLE_BG),
                  new EditorColor(GhostColorScheme.DEPENDENCY_UPDATE_AVAILABLE)));
        }
        checkInBackground(coordinates, dependency.group(), dependency.name());
      }
    }

    editor.setHighlightTexts(container.isEmpty() ? null : container);
  }

  /** Asks Packagist for the newest release once per package and repaints when the answer lands. */
  private void checkInBackground(String coordinates, String vendor, String name) {
    if (!checking.add(coordinates)) {
      return;
    }
    PackagistVersionChecker.check(
        vendor,
        name,
        newest ->
            editor.post(
                () -> {
                  updateCache.put(coordinates, newest == null ? "" : newest);
                  checking.remove(coordinates);
                  if (currentFilePath != null) {
                    refreshHighlights();
                  }
                }));
  }

  // ── Tap handling ─────────────────────────────────────────────────────────

  private void handleSelection(SelectionChangeEvent event) {
    if (editor.getCursor().isSelected()) {
      dismissPopup();
      lastCoordinates = "";
      return;
    }

    DependencyMatch match =
        ComposerManifestParser.findAt(
            editor.getText(), event.getLeft().getLine(), event.getLeft().getColumn());
    if (match == null) {
      dismissPopup();
      lastCoordinates = "";
      return;
    }

    String coordinates = match.coordinates();
    if (coordinates.equals(lastCoordinates) && activePopup != null && activePopup.isShowing()) {
      return;
    }
    lastCoordinates = coordinates;

    String cached = updateCache.get(coordinates);
    if (cached != null && !cached.isEmpty()) {
      showResult(match, cached);
      return;
    }

    showLoading(match);
    PackagistVersionChecker.check(
        match.group(),
        match.name(),
        newest -> {
          if (newest != null && !newest.isEmpty()) {
            updateCache.put(coordinates, newest);
          }
          editor.post(() -> showResult(match, newest));
        });
  }

  private void showLoading(DependencyMatch match) {
    dismissPopup();
    LinearLayout root = makeRoot();
    root.addView(titleLine("Checking Packagist…"));
    root.addView(infoLine(match.coordinates()));
    activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
  }

  private void showResult(DependencyMatch match, String newest) {
    // The user may have moved to another token while the lookup was running.
    if (!match.coordinates().equals(lastCoordinates)) {
      return;
    }
    dismissPopup();

    String current = match.version();
    LinearLayout root = makeRoot();

    if (newest == null || newest.isEmpty()) {
      root.addView(titleLine("No release found"));
      root.addView(infoLine("Current: " + current));
      activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
      return;
    }

    if (ComposerConstraint.isUpdateAvailable(current, newest)) {
      root.addView(titleLine("Current: " + current));
      root.addView(infoLine("Newest: " + newest));
      root.addView(buildUpdateButton(match, current, newest));
    } else {
      root.addView(titleLine("Up to date"));
      root.addView(infoLine("Current: " + current));
    }
    activePopup = EditorPopUp.showCustomViewAtCursor(editor, root);
  }

  private View buildUpdateButton(DependencyMatch match, String current, String newest) {
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
    button.setOnClickListener(v -> applyUpdate(match, ComposerConstraint.bump(current, newest)));
    return button;
  }

  /** Replaces the constraint token in the document, keeping the operator the author used. */
  private void applyUpdate(DependencyMatch match, String replacement) {
    int line = match.versionLine();
    try {
      int startOffset = editor.getText().getIndexer().getCharIndex(line, match.versionStart());
      int endOffset = editor.getText().getIndexer().getCharIndex(line, match.versionEnd());

      editor.getText().beginBatchEdit();
      try {
        editor.getText().replace(startOffset, endOffset, replacement);
      } finally {
        editor.getText().endBatchEdit();
      }

      editor.setSelection(line, match.versionStart() + replacement.length());
      editor.invalidate();
    } catch (RuntimeException ignored) {
      // The document moved under us; the user can retry from the popup.
    }
    dismissPopup();
    lastCoordinates = "";
  }

  // ── View helpers ─────────────────────────────────────────────────────────

  private void dismissPopup() {
    if (activePopup != null) {
      try {
        activePopup.dismiss();
      } catch (RuntimeException ignored) {
        // The popup window is already gone.
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
    view.setTextColor(
        editor.getColorScheme().getColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY));
    view.setText(text);
    return view;
  }

  private TextView infoLine(String text) {
    TextView view = new TextView(editor.getContext());
    view.setGravity(Gravity.CENTER);
    view.setTextSize(12);
    view.setPadding(0, dp(4), 0, 0);
    view.setTextColor(
        editor.getColorScheme().getColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY));
    view.setText(text);
    return view;
  }

  /** Black or white, whichever stays readable on the accent colour. */
  private static int contrastText(int background) {
    float luminance =
        (0.299f * Color.red(background)
                + 0.587f * Color.green(background)
                + 0.114f * Color.blue(background))
            / 255f;
    return luminance > 0.5f ? Color.BLACK : Color.WHITE;
  }

  private int dp(int value) {
    float density = editor.getContext().getResources().getDisplayMetrics().density;
    return (int) (value * density);
  }
}
