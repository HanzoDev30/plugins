package ir.ghostide.haskelllsp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.widget.CodeEditor;
import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

public final class HaskellTextmateHost {

  private static volatile HaskellTextmateHost instance;

  private static final long REASSERT_DELAY_1_MS = 300L;
  private static final long REASSERT_DELAY_2_MS = 900L;
  private static final long REASSERT_DELAY_3_MS = 2500L;
  private static final long REASSERT_DELAY_4_MS = 6000L;

  static final String HASKELL_SCOPE = "source.haskell";
  static final String ASSET = "grammars/haskell.tmLanguage.json";

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;
  private final Set<String> registeredScopes =
      ConcurrentHashMap.newKeySet();

  private volatile boolean ready;
  private volatile Pending pending;

  private HaskellTextmateHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new HaskellTextmateHost(androidContext, logger);
  }

  public static HaskellTextmateHost instance() {
    return instance;
  }

  public void loadAsync() {
    Thread worker = new Thread(this::load, "haskell-tm-loader");
    worker.start();
  }

  private void load() {
    try {
      GrammarDefinition definition =
          DefaultGrammarDefinition.withGrammarSource(
              new PluginAssetGrammarSource(androidContext, ASSET), "haskell-textmate", HASKELL_SCOPE);
      GrammarRegistry.getInstance().loadGrammars(Arrays.asList(definition));
      ready = true;
      logger.info("Haskell TextMate grammar registered (source.haskell)");
    } catch (Exception e) {
      logger.error("Failed to register Haskell TextMate grammar", e);
    }
    main.post(this::flushPending);
  }

  void registerScope(String scope) {
    registeredScopes.add(scope);
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
    Object lang = editor.getEditorLanguage();
    if (lang == null) {
      return false;
    }
    if (lang instanceof io.github.rosemoe.sora.langs.textmate.TextMateLanguage) {
      return true;
    }
    String className = lang.getClass().getName();
    return className.startsWith("io.github.rosemoe.sora.lsp.editor.");
  }

  Language newLanguage(String scopeName) {
    try {
      if (GrammarRegistry.getInstance().findGrammar(scopeName) == null) {
        return null;
      }
      return io.github.rosemoe.sora.langs.textmate.TextMateLanguage.create(
          scopeName, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("haskell: cannot create TextMate language for " + scopeName, e);
      return null;
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