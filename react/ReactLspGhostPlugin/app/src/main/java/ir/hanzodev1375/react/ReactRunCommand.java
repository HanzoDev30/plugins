package ir.hanzodev1375.react;

import ir.hanzodev1375.ghostide.plugin.api.PluginCommand;

/** Command-palette entry that starts the React runner. */
public final class ReactRunCommand implements PluginCommand {

  private final ReactRunner runner;

  public ReactRunCommand(ReactRunner runner) {
    this.runner = runner;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.react.run";
  }

  @Override
  public String getTitle() {
    return "Run React";
  }

  @Override
  public String getDescription() {
    return "اجرای پروژه یا کامپوننت React در ترمینال";
  }

  @Override
  public void execute() {
    runner.run();
  }
}
