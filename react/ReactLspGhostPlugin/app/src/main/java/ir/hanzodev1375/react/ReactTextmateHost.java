package ir.hanzodev1375.react;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.widget.CodeEditor;

import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Registers the TypeScript / TypeScriptReact (TSX) / JavaScript TextMate grammars bundled in the
 * plugin assets and applies the right one to the open editor. The grammars themselves live in the
 * same files VS Code ships, so JSX highlighting matches upstream.
 */
public final class ReactTextmateHost {

  static final String SCOPE_TS = "source.ts";
  static final String SCOPE_TSX = "source.tsx";
  static final String SCOPE_JS = "source.js";

  private static final long[] REASSERT_DELAYS_MS = {300L, 900L, 2500L, 6000L};

  private static final GrammarAsset[] BUNDLED_GRAMMARS = {
    new GrammarAsset("grammars/TypeScript.tmLanguage.json", SCOPE_TS),
    new GrammarAsset("grammars/TypeScriptReact.tmLanguage.json", SCOPE_TSX),
    new GrammarAsset("grammars/JavaScript.tmLanguage.json", SCOPE_JS),
  };

  private static volatile ReactTextmateHost instance;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;
  private final Set<String> registeredScopes = java.util.concurrent.ConcurrentHashMap.newKeySet();

  private volatile boolean ready;
  private volatile Pending pending;

  private ReactTextmateHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new ReactTextmateHost(androidContext, logger);
  }

  public static ReactTextmateHost instance() {
    return instance;
  }

  /** Maps a file extension to the TextMate scope that should highlight it. */
  public static String scopeForExtension(String extension) {
    if (extension == null) {
      return null;
    }
    switch (extension.toLowerCase(Locale.ROOT)) {
      case "tsx":
        return SCOPE_TSX;
      case "ts":
      case "mts":
      case "cts":
        return SCOPE_TS;
      case "jsx":
      case "js":
      case "mjs":
      case "cjs":
        return SCOPE_JS;
      default:
        return null;
    }
  }

  public void loadAsync() {
    Thread worker = new Thread(this::load, "react-tm-loader");
    worker.setDaemon(true);
    worker.start();
  }

  private void load() {
    List<GrammarDefinition> definitions = new ArrayList<>();
    for (GrammarAsset asset : BUNDLED_GRAMMARS) {
      try {
        if (GrammarRegistry.getInstance().findGrammar(asset.scope) != null) {
          logger.info("react: grammar already present, skipping " + asset.scope);
          continue;
        }
        definitions.add(
            DefaultGrammarDefinition.withGrammarSource(
                new PluginAssetGrammarSource(androidContext, asset.path),
                "react-" + asset.id,
                asset.scope));
      } catch (Exception e) {
        logger.warn("react: cannot prepare grammar " + asset.scope, e);
      }
    }
    if (!definitions.isEmpty()) {
      try {
        GrammarRegistry.getInstance().loadGrammars(definitions);
      } catch (Exception e) {
        logger.error("react: failed to register bundled grammars", e);
      }
    }
    try {
      ready = GrammarRegistry.getInstance().findGrammar(SCOPE_TSX) != null;
    } catch (Exception e) {
      ready = false;
    }
    if (ready) {
      logger.info("React TextMate grammars ready");
    } else {
      logger.warn("React TextMate grammars are not available");
    }
    main.post(this::flushPending);
  }

  public void setWhenReady(CodeEditor editor, String scopeName) {
    if (scopeName == null) {
      return;
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
    if (editor == null || editor.isReleased() || alreadyHandled(editor)) {
      return;
    }
    Language lang = newLanguage(scopeName);
    if (lang == null) {
      return;
    }
    editor.setEditorLanguage(lang);
    for (long delay : REASSERT_DELAYS_MS) {
      reassert(editor, scopeName, delay);
    }
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
    // When the LSP-backed language is already active it paints the same scopes; only fall back to
    // our TextMate language when nothing better is installed.
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
        logger.warn("react: no TextMate grammar for scope " + scope);
        return null;
      }
      return TextMateLanguage.create(scope, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("react: cannot create TextMate language for " + scope, e);
      return null;
    }
  }

  private static final class GrammarAsset {
    final String path;
    final String scope;
    final String id;

    GrammarAsset(String path, String scope) {
      this.path = path;
      this.scope = scope;
      this.id = scope.replace("source.", "");
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
