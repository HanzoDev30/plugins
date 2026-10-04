package ir.hanzodev1375.csv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.widget.CodeEditor;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/** Loads the bundled rainbow-CSV grammars and applies them to a CodeEditor. */
public final class CsvTextmateHost {

  static final String SCOPE_COMMA = "source.csv";
  static final String SCOPE_SEMICOLON = "source.csv.semicolon";
  static final String SCOPE_TAB = "source.tsv";
  static final String SCOPE_PIPE = "source.csv.pipe";

  private static final String[][] GRAMMARS = {
    {"grammars/csv.tmLanguage.json", SCOPE_COMMA, "csv-comma"},
    {"grammars/csv-semicolon.tmLanguage.json", SCOPE_SEMICOLON, "csv-semicolon"},
    {"grammars/tsv.tmLanguage.json", SCOPE_TAB, "csv-tab"},
    {"grammars/csv-pipe.tmLanguage.json", SCOPE_PIPE, "csv-pipe"},
  };

  private static final long[] REASSERT_DELAYS_MS = {300L, 900L, 2500L, 6000L};

  private static volatile CsvTextmateHost instance;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;

  private final Map<CodeEditor, Applied> applied =
      Collections.synchronizedMap(new WeakHashMap<>());

  private volatile boolean ready;
  private volatile Pending pending;

  private CsvTextmateHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new CsvTextmateHost(androidContext, logger);
  }

  public static CsvTextmateHost instance() {
    return instance;
  }

  static String scopeFor(char delimiter) {
    switch (delimiter) {
      case ';':
        return SCOPE_SEMICOLON;
      case '\t':
        return SCOPE_TAB;
      case '|':
        return SCOPE_PIPE;
      default:
        return SCOPE_COMMA;
    }
  }

  public void loadAsync() {
    new Thread(this::load, "csv-tm-loader").start();
  }

  private void load() {
    List<GrammarDefinition> definitions = new ArrayList<>();
    for (String[] g : GRAMMARS) {
      try {
        if (GrammarRegistry.getInstance().findGrammar(g[1]) != null) {
          logger.info("csv: grammar already present, skipping " + g[1]);
          continue;
        }
        definitions.add(
            DefaultGrammarDefinition.withGrammarSource(
                new PluginAssetGrammarSource(androidContext, g[0]), g[2], g[1]));
      } catch (Exception e) {
        logger.warn("csv: cannot prepare grammar " + g[1], e);
      }
    }
    if (!definitions.isEmpty()) {
      try {
        GrammarRegistry.getInstance().loadGrammars(definitions);
      } catch (Exception e) {
        logger.error("csv: failed to register grammars", e);
      }
    }
    try {
      ready = GrammarRegistry.getInstance().findGrammar(SCOPE_COMMA) != null;
    } catch (Exception e) {
      ready = false;
    }
    if (ready) {
      logger.info("CSV TextMate grammars ready");
    } else {
      logger.warn("CSV TextMate grammars are not available");
    }
    for (String[] g : GRAMMARS) {
      boolean found;
      try {
        found = GrammarRegistry.getInstance().findGrammar(g[1]) != null;
      } catch (Exception e) {
        found = false;
      }
      logger.info("csv: grammar " + g[1] + " registered = " + found);
    }
    selfTest(SCOPE_COMMA, "id,name,\"a,b\",3.5\n");
    selfTest(SCOPE_TAB, "id\tname\tscore\n");
    main.post(this::flushPending);
  }

  /** Tokenizes a sample line with the real engine and logs the scopes: proves the grammar works. */
  private void selfTest(String scope, String line) {
    try {
      IGrammar grammar = GrammarRegistry.getInstance().findGrammar(scope);
      if (grammar == null) {
        logger.warn("csv: selftest " + scope + ": grammar is null");
        return;
      }
      ITokenizeLineResult<IToken[]> result = grammar.tokenizeLine(line);
      StringBuilder sb = new StringBuilder();
      for (IToken t : result.getTokens()) {
        int end = Math.min(t.getEndIndex(), line.length());
        sb.append('[')
            .append(line.substring(Math.min(t.getStartIndex(), end), end).replace("\n", "\\n").replace("\t", "\\t"))
            .append("]=")
            .append(t.getScopes().size() > 1 ? t.getScopes().get(t.getScopes().size() - 1) : t.getScopes())
            .append(' ');
      }
      logger.info("csv: selftest " + scope + " -> " + sb);
    } catch (Throwable e) {
      logger.error("csv: selftest " + scope + " FAILED", e);
    }
  }

  public void setWhenReady(CodeEditor editor, String scopeName) {
    if (!ready) {
      logger.info("csv: grammar not ready yet, queued " + scopeName);
    }
    main.post(
        () -> {
          if (ready) {
            apply(editor, scopeName);
          } else {
            pending = new Pending(editor, scopeName);
          }
        });
  }

  private void flushPending() {
    Pending p = pending;
    if (p != null && ready) {
      pending = null;
      apply(p.editor, p.scopeName);
    }
  }

  private void apply(CodeEditor editor, String scopeName) {
    if (editor.isReleased() || isLsp(editor) || isOurs(editor, scopeName)) {
      return;
    }
    install(editor, scopeName);
    for (long delay : REASSERT_DELAYS_MS) {
      reassert(editor, scopeName, delay);
    }
  }

  private void install(CodeEditor editor, String scopeName) {
    Language lang = newLanguage(scopeName);
    if (lang == null) {
      return;
    }
    applied.put(editor, new Applied(lang, scopeName));
    editor.setEditorLanguage(lang);
    Language now = editor.getEditorLanguage();
    logger.info(
        "csv: applied " + scopeName + ", editor language now = "
            + (now == null ? "null" : now.getClass().getName()));
  }

  /** The host (or its default TextMate/plain language) may set its own language after we did. */
  private void reassert(CodeEditor editor, String scopeName, long delay) {
    editor.postDelayed(
        () -> {
          if (ready && !editor.isReleased() && !isLsp(editor) && !isOurs(editor, scopeName)) {
            install(editor, scopeName);
          }
        },
        delay);
  }

  private boolean isOurs(CodeEditor editor, String scopeName) {
    Applied a = applied.get(editor);
    return a != null && a.scope.equals(scopeName) && editor.getEditorLanguage() == a.language;
  }

  private static boolean isLsp(CodeEditor editor) {
    Language lang = editor.getEditorLanguage();
    return lang != null && lang.getClass().getName().startsWith("io.github.rosemoe.sora.lsp.editor.");
  }

  private Language newLanguage(String scopeName) {
    String scope = scopeName == null ? "" : scopeName.toLowerCase(Locale.ROOT);
    if (scope.isEmpty()) {
      return null;
    }
    try {
      if (GrammarRegistry.getInstance().findGrammar(scope) == null) {
        logger.warn("csv: no TextMate grammar for scope " + scope);
        return null;
      }
      return TextMateLanguage.create(scope, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("csv: cannot create TextMate language for " + scope, e);
      return null;
    }
  }

  private static final class Applied {
    final Language language;
    final String scope;

    Applied(Language language, String scope) {
      this.language = language;
      this.scope = scope;
    }
  }

  private static final class Pending {
    final CodeEditor editor;
    final String scopeName;

    Pending(CodeEditor editor, String scopeName) {
      this.editor = editor;
      this.scopeName = scopeName;
    }
  }
}
