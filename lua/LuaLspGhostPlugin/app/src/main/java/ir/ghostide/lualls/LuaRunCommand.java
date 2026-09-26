package ir.ghostide.lualls;

import android.content.Context;
import android.content.Intent;

import java.io.File;

import ir.hanzodev1375.ghostide.ide.ui.api.CodeRunnerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.FileManagerHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.UiFeedbackHost;
import ir.hanzodev1375.ghostide.plugin.api.PluginCommand;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * Command-palette action: runs the Lua file that is currently open in the editor.
 *
 * <p>The command is handed to the host terminal through an intent (the same route the plugin
 * manager uses for setup commands), and falls back to the host code runner if that fails.
 */
public final class LuaRunCommand implements PluginCommand {

  private static final String TERMINAL_ACTIVITY =
      "ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity";
  private static final String EXTRA_COMMAND = "command";

  private final PluginContext context;

  public LuaRunCommand(PluginContext context) {
    this.context = context;
  }

  @Override
  public String getId() {
    return "ir.ghostide.lualls.run";
  }

  @Override
  public String getTitle() {
    return "Lua: Run current file";
  }

  @Override
  public String getDescription() {
    return "Runs the open .lua file in the IDE terminal (luajit, lua5.4 or lua).";
  }

  @Override
  public void execute() {
    File file = resolveTarget();
    if (file == null) {
      toast("No .lua file is open");
      return;
    }
    String command = buildCommand(file);
    Context appContext = context.getServices().get(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    if (appContext != null) {
      try {
        Intent intent = new Intent(TERMINAL_ACTIVITY);
        intent.putExtra(EXTRA_COMMAND, command);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        toast("Running " + file.getName());
        return;
      } catch (RuntimeException e) {
        context.getLogger().warn("Terminal intent failed, falling back to code runner", e);
      }
    }
    CodeRunnerHost runner = context.getServices().get(IdeHostServices.CODE_RUNNER_HOST);
    if (runner == null) {
      toast("Terminal is not available");
      return;
    }
    runner.runShell(command, false);
    toast("Running " + file.getName());
  }

  private static String buildCommand(File file) {
    String dir = file.getParent() == null ? "." : file.getParent();
    String name = shellQuote(file.getName());
    return "cd "
        + shellQuote(dir)
        + " && { command -v luajit >/dev/null 2>&1 && exec luajit "
        + name
        + "; } || { command -v lua5.4 >/dev/null 2>&1 && exec lua5.4 "
        + name
        + "; } || exec lua "
        + name;
  }

  private File resolveTarget() {
    EditorHost editor = context.getServices().get(IdeHostServices.EDITOR_HOST);
    File open = editor == null ? null : editor.getOpenFile();
    if (isLua(open)) {
      return open;
    }
    FileManagerHost files = context.getServices().get(IdeHostServices.FILE_MANAGER_HOST);
    File selected = files == null ? null : files.getSelectedFile();
    return isLua(selected) ? selected : null;
  }

  private static boolean isLua(File file) {
    if (file == null || !file.isFile()) {
      return false;
    }
    return file.getName().toLowerCase().endsWith(".lua");
  }

  private void toast(String message) {
    UiFeedbackHost feedback = context.getServices().get(IdeHostServices.UI_FEEDBACK);
    if (feedback != null) {
      feedback.toast(message, false);
    }
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}
