package ir.ghostide.tomllsp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.widget.CodeEditor;

import ir.hanzodev1375.ghostide.plugin.api.PluginLogger;

/**
 * Installs the bundled TOML TextMate grammar and puts it on the editor.
 *
 * <p>The grammar ships inside the plugin instead of being fetched at runtime, because highlighting
 * is the one thing that has to work before - or entirely without - the language server: TOML
 * opened on a phone with no server installed still has to be readable. {@code source.toml} covers
 * .toml and the many config files that ship with it (Cargo.toml, pyproject.toml, ...) in one
 * grammar, so one registry entry serves every file.
 *
 * <p>The language server declares the same scope through
 * {@link ir.hanzodev1375.ghostide.ide.api.LspServerDefinition#grammarScopeName}, and the editor
 * may hand the language back to us afterwards - a second {@code setEditorLanguage} from the host's
 * own LSP path. Re-asserting on a short ladder of delays makes that race lose: whichever side
 * applies last, the TOML file ends up highlighted.
 */
public final class TomlTextmateHost {

  public static final String TOML_SCOPE = "source.toml";

  private static final String GRAMMAR_ASSET = "grammars/TOML.tmLanguage.json";

  private static final long REASSERT_DELAY_1_MS = 300L;
  private static final long REASSERT_DELAY_2_MS = 900L;
  private static final long REASSERT_DELAY_3_MS = 2500L;
  private static final long REASSERT_DELAY_4_MS = 6000L;

  private static volatile TomlTextmateHost instance;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Context androidContext;
  private final PluginLogger logger;
  private final Set<String> registeredScopes = ConcurrentHashMap.newKeySet();

  private volatile boolean ready;
  private volatile Pending pending;

  private TomlTextmateHost(Context androidContext, PluginLogger logger) {
    this.androidContext = androidContext;
    this.logger = logger;
  }

  public static void create(Context androidContext, PluginLogger logger) {
    instance = new TomlTextmateHost(androidContext, logger);
  }

  public static TomlTextmateHost instance() {
    return instance;
  }

  public void loadAsync() {
    Thread worker = new Thread(this::load, "toml-tm-loader");
    worker.start();
  }

  private void load() {
    List<GrammarDefinition> definitions = new ArrayList<>();
    try {
      if (GrammarRegistry.getInstance().findGrammar(TOML_SCOPE) == null) {
        GrammarDefinition definition =
            DefaultGrammarDefinition.withGrammarSource(
                new PluginAssetGrammarSource(androidContext, GRAMMAR_ASSET), "toml", TOML_SCOPE);
        definitions.add(definition);
      }
    } catch (Exception e) {
      logger.warn("toml: cannot prepare grammar " + TOML_SCOPE, e);
    }

    if (!definitions.isEmpty()) {
      try {
        GrammarRegistry.getInstance().loadGrammars(definitions);
      } catch (Exception e) {
        logger.error("toml: failed to register the bundled TOML grammar", e);
      }
    }

    try {
      ready = GrammarRegistry.getInstance().findGrammar(TOML_SCOPE) != null;
    } catch (Exception e) {
      ready = false;
    }

    if (ready) {
      logger.info("TOML TextMate grammar ready (" + TOML_SCOPE + ")");
    } else {
      logger.warn("TOML TextMate grammar is not available");
    }
    main.post(this::flushPending);
  }

  /** Called by the provider when the language server asks for this grammar. */
  public void registerScope(String scope) {
    if (registeredScopes.add(scope)) {
      logger.info("toml: scope " + scope + " requested by the language server");
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
    Pending waiting = pending;
    if (waiting != null && ready) {
      pending = null;
      apply(waiting.editor, waiting.scopeName);
    }
  }

  private void apply(CodeEditor editor, String scopeName) {
    if (editor.isReleased() || alreadyHandled(editor)) {
      return;
    }
    Language language = newLanguage(scopeName);
    if (language == null) {
      return;
    }
    editor.setEditorLanguage(language);
    reassert(editor, scopeName, REASSERT_DELAY_1_MS);
    reassert(editor, scopeName, REASSERT_DELAY_2_MS);
    reassert(editor, scopeName, REASSERT_DELAY_3_MS);
    reassert(editor, scopeName, REASSERT_DELAY_4_MS);
  }

  private void reassert(CodeEditor editor, String scopeName, long delay) {
    editor.postDelayed(
        () -> {
          if (ready && !editor.isReleased() && !alreadyHandled(editor)) {
            Language language = newLanguage(scopeName);
            if (language != null) {
              editor.setEditorLanguage(language);
            }
          }
        },
        delay);
  }

  /**
   * The editor swaps in a language on its own whenever an LSP session starts, which throws away a
   * grammar that was applied a moment earlier. A TextMate language is the one the host itself
   * installs for a server that declared a scope, so either of those means the work is already done
   * and touching it again would only restart tokenisation.
   */
  private boolean alreadyHandled(CodeEditor editor) {
    Language language = editor.getEditorLanguage();
    if (language == null) {
      return false;
    }
    if (language instanceof TextMateLanguage) {
      return true;
    }
    return language.getClass().getName().startsWith("io.github.rosemoe.sora.lsp.editor.");
  }

  Language newLanguage(String scopeName) {
    String scope = scopeName == null ? "" : scopeName.toLowerCase(Locale.ROOT);
    if (scope.isEmpty()) {
      return null;
    }
    try {
      if (GrammarRegistry.getInstance().findGrammar(scope) == null) {
        logger.warn("toml: no TextMate grammar for scope " + scope);
        return null;
      }
      return TextMateLanguage.create(scope, GrammarRegistry.getInstance(), true);
    } catch (Exception e) {
      logger.warn("toml: cannot create TextMate language for " + scope, e);
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
