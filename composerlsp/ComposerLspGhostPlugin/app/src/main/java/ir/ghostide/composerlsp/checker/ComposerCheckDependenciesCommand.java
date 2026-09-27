package ir.ghostide.composerlsp.checker;

import ir.hanzodev1375.ghostide.plugin.api.PluginCommand;

/** Command palette entry that forgets cached answers and asks Packagist again for the open tab. */
public final class ComposerCheckDependenciesCommand implements PluginCommand {

  private static final String ID = "ir.ghostide.composerlsp.checkDependencies";

  private final ComposerCheckerService service;

  public ComposerCheckDependenciesCommand(ComposerCheckerService service) {
    this.service = service;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getTitle() {
    return "Composer: check dependency versions";
  }

  @Override
  public String getDescription() {
    return "Looks up every require/require-dev entry of the open composer.json on Packagist and "
        + "highlights the ones whose newest release is not covered by the declared constraint.";
  }

  @Override
  public void execute() {
    service.recheck();
  }
}
