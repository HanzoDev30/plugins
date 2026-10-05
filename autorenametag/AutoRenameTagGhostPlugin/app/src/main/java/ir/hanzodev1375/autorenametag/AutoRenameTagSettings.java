package ir.hanzodev1375.autorenametag;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import ir.hanzodev1375.ghostide.plugin.api.PluginStorage;

public final class AutoRenameTagSettings {

  private static final String[] HTML_EXTENSIONS = {"html", "htm", "shtml", "xhtml"};

  private static final String[] TEMPLATE_EXTENSIONS = {
    "vue",
    "svelte",
    "astro",
    "pug",
    "jade",
    "hbs",
    "handlebars",
    "mustache",
    "ejs",
    "twig",
    "njk",
    "liquid",
    "haml",
    "slim",
    "jsp",
    "jspx",
    "jsw",
    "jspf",
    "php",
    "php3",
    "php4",
    "php5",
    "phtml",
    "erb",
    "rhtml",
    "blade",
    "volt",
    "latte",
    "marko",
    "riot",
    "mjml",
    "cshtml",
    "vbhtml",
    "razor",
    "aspx",
    "master",
    "ascx"
  };

  private static final String[] MARKUP_EXTENSIONS = {
    "xml",
    "svg",
    "xsl",
    "xslt",
    "dtd",
    "ent",
    "plist",
    "rss",
    "atom",
    "xsd",
    "wsdl",
    "xaml",
    "resx",
    "iml",
    "pom",
    "fxml",
    "xib",
    "storyboard",
    "csproj",
    "vbproj",
    "fsproj",
    "props",
    "targets",
    "config",
    "manifest",
    "nuspec",
    "qrc",
    "ui",
    "rc",
    "wxi",
    "wxl",
    "wxs"
  };

  public enum Scope {
    HTML("html"),
    MARKUP("markup"),
    TEMPLATES("templates");

    private final String id;
    private Set<String> extensions;

    Scope(String id) {
      this.id = id;
    }

    public String id() {
      return id;
    }

    public static Scope from(String value) {
      for (Scope scope : values()) {
        if (scope.id.equals(value)) {
          return scope;
        }
      }
      return TEMPLATES;
    }

    public static Scope next(Scope current) {
      Scope[] all = values();
      return all[(current.ordinal() + 1) % all.length];
    }

    public boolean accepts(String extension) {
      return extension != null && !extension.isEmpty() && extensions().contains(extension);
    }

    public Set<String> extensions() {
      Set<String> cached = extensions;
      if (cached != null) {
        return cached;
      }
      switch (this) {
        case HTML:
          cached = setOf(HTML_EXTENSIONS);
          break;
        case MARKUP:
          cached = setOf(concat(HTML_EXTENSIONS, MARKUP_EXTENSIONS));
          break;
        default:
          cached = setOf(concat(MARKUP_EXTENSIONS, concat(HTML_EXTENSIONS, TEMPLATE_EXTENSIONS)));
      }
      extensions = cached;
      return cached;
    }

    private static Set<String> setOf(String[] values) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    private static String[] concat(String[] first, String[] second) {
      String[] merged = Arrays.copyOf(first, first.length + second.length);
      System.arraycopy(second, 0, merged, first.length, second.length);
      return merged;
    }
  }

  private final PluginStorage storage;

  public AutoRenameTagSettings(PluginStorage storage) {
    this.storage = storage;
  }

  public boolean enabled() {
    return storage.getBoolean("auto_rename_tag.enabled", true);
  }

  public void setEnabled(boolean enabled) {
    storage.putBoolean("auto_rename_tag.enabled", enabled);
  }

  public Scope scope() {
    return Scope.from(storage.getString("auto_rename_tag.scope", Scope.TEMPLATES.id()));
  }

  public void setScope(Scope scope) {
    storage.putString("auto_rename_tag.scope", scope.id());
  }

  public static boolean looksLikeMarkup(String simpleName) {
    if (simpleName == null) {
      return false;
    }
    String lower = simpleName.toLowerCase(Locale.ROOT);
    return lower.contains("html")
        || lower.contains("xml")
        || lower.contains("vue")
        || lower.contains("svelte")
        || lower.contains("astro")
        || lower.contains("handlebars")
        || lower.contains("jsp");
  }
}
