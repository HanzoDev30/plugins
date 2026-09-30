package ir.hanzodev1375.astro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;

import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

public final class AstroTextmateHost {

  private static volatile AstroTextmateHost instance;

  static final String ASTRO_SCOPE = "source.astro";

  private static final long REASSERT_DELAY_1_MS = 300L;
  private static final long REASSERT_DELAY_2_MS = 900L;
  private static final long REASSERT_DELAY_3_MS = 2500L;
  private static final long REASSERT_DELAY_4_MS = 6000L;

  private static final GrammarAsset[] BUNDLED_GRAMMARS = {
    new GrammarAsset("grammars/TypeScript.tmLanguage.json", "source.ts"),
    new GrammarAsset("grammars/TypeScriptReact.tmLanguage.json", "source.tsx"),
    new GrammarAsset("grammars/JavaScript.tmLanguage.json", "source.js"),
    new GrammarAsset("grammars/css.tmLanguage.json", "source.css"),
    new GrammarAsset("grammars/JSON.tmLanguage.json", "source.json"),
    new GrammarAsset("grammars/astro.tmLanguage.json", ASTRO_SCOPE, List.of(
        "source.ts",
        "source.tsx",
        "source.js",
        "source.css",
        "source.json",
        "source.css.less",
        "source.css.postcss",
        "source.css.scss",
        "source.sass",
        "source.stylus")),
  };

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;
  private final Set<String> registeredScopes = java.util.concurrent.ConcurrentHashMap.newKeySet();

  private volatile boolean ready;
  private volatile Pending pending;

  private AstroTextmateHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new AstroTextmateHost(androidContext, logger);
  }

  public static AstroTextmateHost instance() {
    return instance;
  }

  public void loadAsync() {
    Thread worker = new Thread(this::load, "astro-tm-loader");
    worker.start();
  }

  private void load() {
    List<GrammarDefinition> definitions = new ArrayList<>();
    for (GrammarAsset asset : BUNDLED_GRAMMARS) {
      try {
        if (GrammarRegistry.getInstance().findGrammar(asset.scope) != null) {
          logger.info("astro: grammar already present, skipping " + asset.scope);
          if (asset.scope.equals(ASTRO_SCOPE)) {
            ready = true;
          }
          continue;
        }
        GrammarDefinition definition =
            DefaultGrammarDefinition.withGrammarSource(
                new PluginAssetGrammarSource(androidContext, asset.path),
                "astro-" + asset.id,
                asset.scope);
        if (!asset.embedded.isEmpty()) {
          definition = ((DefaultGrammarDefinition) definition).withEmbeddedLanguages(asset.embedded);
        }
        definitions.add(definition);
      } catch (Exception e) {
        logger.warn("astro: cannot prepare grammar " + asset.scope, e);
      }
    }
    if (!definitions.isEmpty()) {
      try {
        GrammarRegistry.getInstance().loadGrammars(definitions);
      } catch (Exception e) {
        logger.error("astro: failed to register bundled grammars", e);
      }
    }
    try {
      ready = GrammarRegistry.getInstance().findGrammar(ASTRO_SCOPE) != null;
    } catch (Exception e) {
      ready = false;
    }
    if (ready) {
      logger.info("Astro TextMate grammar ready (" + ASTRO_SCOPE + ")");
    } else {
      logger.warn("Astro TextMate grammar is not available");
    }
    main.post(this::flushPending);
  }

  void registerScope(String scope) {
    if (registeredScopes.add(scope)) {
      logger.info("astro: scope " + scope + " requested by the language server");
    }
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
    if (editor.isReleased() || alreadyHandled(editor)) {
      return;
    }
    Language lang = newLanguage(scopeName);
    if (lang == null) {
      return;
    }
    editor.setEditorLanguage(lang);
    reassert(editor, scopeName, REASSERT_DELAY_1_MS);
    reassert(editor, scopeName, REASSERT_DELAY_2_MS);
    reassert(editor, scopeName, REASSERT_DELAY_3_MS);
    reassert(editor, scopeName, REASSERT_DELAY_4_MS);
  }

  private void reassert(CodeEditor editor, String scopeName, long delay) {
    editor.postDelayed(
        () -> {
          if (ready && !editor.isReleased() && !alreadyHandled(editor)) {
            Language lang = newLanguage(scopeName);
            if (lang != null) {
              editor.setEditorLanguage(lang);
            }
          }
        },
        delay);
  }

  private boolean alreadyHandled(CodeEditor editor) {
    Language lang = editor.getEditorLanguage();
    if (lang == null) {
      return false;
    }
    if (lang instanceof TextMateLanguage) {
      return true;
    }
    return lang.getClass().getName().startsWith("io.github.rosemoe.sora.lsp.editor.");
  }

  Language newLanguage(String scopeName) {
    String scope = scopeName == null ? "" : scopeName.toLowerCase(Locale.ROOT);
    if (scope.isEmpty()) {
      return null;
    }
    try {
      if (GrammarRegistry.getInstance().findGrammar(scope) == null) {
        logger.warn("astro: no TextMate grammar for scope " + scope);
        return null;
      }
      return TextMateLanguage.create(scope, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("astro: cannot create TextMate language for " + scope, e);
      return null;
    }
  }

  private static final class GrammarAsset {
    final String path;
    final String scope;
    final Map<String, String> embedded;

    GrammarAsset(String path, String scope) {
      this(path, scope, List.of());
    }

    GrammarAsset(String path, String scope, List<String> embeddedScopes) {
      this.path = path;
      this.scope = scope;
      this.embedded = Map.copyOf(embeddedScopeMap(embeddedScopes));
      this.id = scope.replace("source.", "");
    }

    private static Map<String, String> embeddedScopeMap(List<String> scopes) {
      Map<String, String> map = new LinkedHashMap<>();
      for (String embedded : scopes) {
        map.put(embedded, embedded);
      }
      return map;
    }

    final String id;
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