package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Sends a command to the host terminal, the same way the plugin manager's "run setup" button does.
 *
 * <p>The terminal activity is addressed by name so the plugin keeps compiling against the plugin
 * API only. If that ever fails, the host's own code runner is used as a second chance.
 */
final class TerminalLauncher {

  private static final String TERMINAL_ACTIVITY =
      "ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity";
  private static final String EXTRA_COMMAND = "command";

  private final PluginContext plugin;
  private final Context context;

  TerminalLauncher(PluginContext plugin, Context context) {
    this.plugin = plugin;
    this.context = context;
  }

  void run(String command) {
    if (command == null || command.trim().isEmpty()) {
      return;
    }
    try {
      Intent intent = new Intent(TERMINAL_ACTIVITY);
      intent.putExtra(EXTRA_COMMAND, command);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    } catch (RuntimeException e) {
      plugin.getLogger().warn("Terminal intent failed, falling back to the code runner", e);
      CodeRunnerHost runner = plugin.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
      if (runner == null) {
        Toast.makeText(context, "ترمینال در دسترس نیست", Toast.LENGTH_LONG).show();
        return;
      }
      runner.runShell(command, false);
    }
  }

  void toast(String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
  }
}
