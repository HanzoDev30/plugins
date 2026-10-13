package ir.hanzodev1375.jadx.bridge;

/** Summary of a finished (or cancelled) "decompile all" run. */
public final class SaveResult {

  public final int classes;
  public final int errors;
  public final int warnings;
  public final boolean cancelled;
  public final long millis;

  public SaveResult(int classes, int errors, int warnings, boolean cancelled, long millis) {
    this.classes = classes;
    this.errors = errors;
    this.warnings = warnings;
    this.cancelled = cancelled;
    this.millis = millis;
  }
}
