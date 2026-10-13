package ir.hanzodev1375.jadx;

import java.util.Locale;

/** Tiny two-language string helper (English / Persian) so the plugin needs no resources.arsc. */
final class L {

  private static final boolean FA = "fa".equals(Locale.getDefault().getLanguage());

  private L() {}

  static String t(String en, String fa) {
    return FA ? fa : en;
  }
}
