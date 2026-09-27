package ir.ghostide.composerlsp;

import android.content.Context;
import android.content.Intent;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Sends the install script to the IDE terminal on its own.
 *
 * <p>The language server is a binary that has to be installed inside the rootfs, and the only place
 * that can do that is the terminal. Rather than waiting for the user to find the setup action in the
 * plugin manager, {@link ComposerLspPlugin} hands the script over as soon as the plugin activates,
 * through the same terminal intent the host itself uses, and falls back to the host code runner when
 * that route is unavailable.
 */
final class ComposerLspInstaller {

  private static final String TERMINAL_ACTIVITY =
      "ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity";
  private static final String EXTRA_COMMAND = "command";

  private ComposerLspInstaller() {}

  static void installNow(PluginContext context, String command) {
    Context appContext = context.getServices().get(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    if (appContext != null) {
      try {
        Intent intent = new Intent(TERMINAL_ACTIVITY);
        intent.putExtra(EXTRA_COMMAND, command);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        toast(context, "Installing PHP, Composer and the manifest language server…");
        return;
      } catch (RuntimeException e) {
        context.getLogger().warn("Terminal intent failed, falling back to code runner", e);
      }
    }
    CodeRunnerHost runner = context.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
    if (runner == null) {
      toast(context, "Terminal is not available - run the setup action from the Plugin Manager");
      return;
    }
    runner.runShell(command, false);
    toast(context, "Installing PHP, Composer and the manifest language server…");
  }

  private static void toast(PluginContext context, String message) {
    UiFeedbackHost feedback = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message, false);
    }
  }
}
