package ir.ghostide.kmplsp;

import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/** Routes {@code .kts} files (Gradle build scripts) to kmp-lsp. */
public final class GradleKtsKmpLspProvider extends KmpLspProvider {

  public GradleKtsKmpLspProvider(ProotProcessLauncher launcher) {
    super(launcher, "kts");
  }

  @Override
  public String getId() {
    return "ir.ghostide.kmplsp.gradle";
  }

  @Override
  public String getDisplayName() {
    return "kmp-lsp (Gradle Kotlin)";
  }
}
