package ir.ghostide.kmplsp;

import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;

/** Routes {@code .swift} files to kmp-lsp. */
public final class SwiftKmpLspProvider extends KmpLspProvider {

  public SwiftKmpLspProvider(ProotProcessLauncher launcher) {
    super(launcher, "swift");
  }

  @Override
  public String getId() {
    return "ir.ghostide.kmplsp.swift";
  }

  @Override
  public String getDisplayName() {
    return "kmp-lsp (Swift)";
  }
}
