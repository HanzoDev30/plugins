package ir.hanzodev1375.svelte;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/** Registers the bundled TextMate grammars and applies them to editors of files without an LSP. */
public final class SvelteGrammarHost {

  /** {asset path, scope, comma separated embedded scopes}. Order matters: dependencies first. */
  private static final String[][] BUNDLED = {
    {"grammars/TypeScript.tmLanguage.json", "source.ts", ""},
    {"grammars/css.tmLanguage.json", "source.css", ""},
    {"grammars/svelte.tmLanguage.json", "source.svelte", "source.ts,source.css,source.css.scss,source.css.less"}
  };

  /** Scopes this plugin is responsible for: the host is "ready" when all of them are registered. */
  private static final String[] OWN_SCOPES = {"source.svelte"};

  private static final long[] REASSERT_DELAYS_MS = {300L, 900L, 2500L, 6000L};

  private static volatile SvelteGrammarHost instance;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;
  private final Map<CodeEditor, Applied> applied = Collections.synchronizedMap(new WeakHashMap<>());

  private volatile boolean ready;
  private volatile Pending pending;

  private SvelteGrammarHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new SvelteGrammarHost(androidContext, logger);
  }

  public static SvelteGrammarHost instance() {
    return instance;
  }

  public void loadAsync() {
    new Thread(this::load, "svelte-tm-loader").start();
  }

  private void load() {
    List<GrammarDefinition> definitions = new ArrayList<>();
    for (String[] g : BUNDLED) {
      try {
        if (GrammarRegistry.getInstance().findGrammar(g[1]) != null) {
          logger.info("svelte: grammar already present, skipping " + g[1]);
          continue;
        }
        DefaultGrammarDefinition definition =
            DefaultGrammarDefinition.withGrammarSource(
                new PluginAssetGrammarSource(androidContext, g[0]), "svelte-" + g[1], g[1]);
        if (!g[2].isEmpty()) {
          Map<String, String> embedded = new LinkedHashMap<>();
          for (String scope : g[2].split(",")) {
            embedded.put(scope, scope);
          }
          definitions.add(definition.withEmbeddedLanguages(embedded));
          continue;
        }
        definitions.add(definition);
      } catch (Exception e) {
        logger.warn("svelte: cannot prepare grammar " + g[1], e);
      }
    }
    if (!definitions.isEmpty()) {
      try {
        GrammarRegistry.getInstance().loadGrammars(definitions);
      } catch (Exception e) {
        logger.error("svelte: failed to register bundled grammars", e);
      }
    }
    boolean all = true;
    for (String scope : OWN_SCOPES) {
      boolean found;
      try {
        found = GrammarRegistry.getInstance().findGrammar(scope) != null;
      } catch (Exception e) {
        found = false;
      }
      logger.info("svelte: grammar " + scope + " registered = " + found);
      all &= found;
    }
    ready = all;
    main.post(this::flushPending);
  }

  public void setWhenReady(CodeEditor editor, String scopeName) {
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
      editor.postDelayed(
          () -> {
            if (ready && !editor.isReleased() && !isLsp(editor) && !isOurs(editor, scopeName)) {
              install(editor, scopeName);
            }
          },
          delay);
    }
  }

  private void install(CodeEditor editor, String scopeName) {
    Language lang = newLanguage(scopeName);
    if (lang == null) {
      return;
    }
    applied.put(editor, new Applied(lang, scopeName));
    editor.setEditorLanguage(lang);
    logger.info("svelte: applied " + scopeName);
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
        logger.warn("svelte: no TextMate grammar for scope " + scope);
        return null;
      }
      return TextMateLanguage.create(scope, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("svelte: cannot create TextMate language for " + scope, e);
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
