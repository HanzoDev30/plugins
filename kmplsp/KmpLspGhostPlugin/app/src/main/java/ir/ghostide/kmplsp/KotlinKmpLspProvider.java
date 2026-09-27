package ir.ghostide.kmplsp;

import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/** Routes {@code .kt} files to kmp-lsp. */
public final class KotlinKmpLspProvider extends KmpLspProvider {

  public KotlinKmpLspProvider(ProotProcessLauncher launcher) {
    super(launcher, "kt");
  }

  @Override
  public String getId() {
    return "ir.ghostide.kmplsp.kotlin";
  }

  @Override
  public String getDisplayName() {
    return "kmp-lsp (Kotlin)";
  }
}
