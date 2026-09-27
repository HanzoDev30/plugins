package ir.ghostide.kmplsp;

import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/** Routes {@code .java} files to kmp-lsp. */
public final class JavaKmpLspProvider extends KmpLspProvider {

  public JavaKmpLspProvider(ProotProcessLauncher launcher) {
    super(launcher, "java");
  }

  @Override
  public String getId() {
    return "ir.ghostide.kmplsp.java";
  }

  @Override
  public String getDisplayName() {
    return "kmp-lsp (Java)";
  }
}
