package ir.hanzodev1375.jadx.bridge;

/** Progress callback of {@link JadxSession#saveAll}. Called from the engine worker thread. */
@FunctionalInterface
public interface ProgressListener {

  void onProgress(int done, int total);
}
