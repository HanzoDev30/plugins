package ir.hanzodev30.gitclone;

import org.eclipse.jgit.lib.ProgressMonitor;

final class JgitProgressMonitor implements ProgressMonitor {

  interface Sink {
    void onPhase(String title, int percent);
  }

  private final Sink sink;

  private volatile boolean cancelled;
  private String title = "";
  private int totalWork;
  private int completed;

  JgitProgressMonitor(Sink sink) {
    this.sink = sink;
  }

  void cancel() {
    cancelled = true;
  }

  @Override
  public void start(int totalTasks) {}

  @Override
  public void beginTask(String title, int totalWork) {
    this.title = title == null ? "" : title;
    this.totalWork = totalWork;
    this.completed = 0;
    sink.onPhase(this.title, totalWork > 0 ? 0 : -1);
  }

  @Override
  public void update(int completed) {
    this.completed += completed;
    int percent =
        totalWork > 0
            ? (int) Math.min(100L, (long) this.completed * 100L / totalWork)
            : -1;
    sink.onPhase(title, percent);
  }

  @Override
  public void endTask() {
    sink.onPhase(title, 100);
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  public void showDuration(boolean enabled) {}
}
