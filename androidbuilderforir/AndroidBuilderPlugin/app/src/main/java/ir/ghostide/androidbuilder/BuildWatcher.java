package ir.ghostide.androidbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Turns a build that runs in the terminal into something the panel can react to.
 *
 * <p>There is no callback to wait for - the build is a proot process - so the marker file is polled
 * instead. A result is reported once, when its timestamp is newer than the one already shown, which
 * is also what makes a build that finished while the panel was closed show up when it opens again.
 */
final class BuildWatcher {

  interface Listener {
    void onBuildFinished(BuildResult result);
  }

  private static final String KEY_ACK = "acknowledged-at";
  private static final long POLL_MS = 2000L;
  private static final long GIVE_UP_MS = 45L * 60L * 1000L;

  private final Context context;
  private final SharedPreferences preferences;
  private final Listener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable poll = this::check;
  private boolean polling;
  private long startedAt;

  BuildWatcher(Context context, SharedPreferences preferences, Listener listener) {
    this.context = context;
    this.preferences = preferences;
    this.listener = listener;
  }

  /** Called the moment a build is handed to the terminal. */
  void arm() {
    startedAt = SystemClock.elapsedRealtime();
    polling = true;
    handler.removeCallbacks(poll);
    handler.postDelayed(poll, POLL_MS);
  }

  /** Called when the panel opens or is refreshed: picks up a build that finished meanwhile. */
  void checkPending() {
    handler.removeCallbacks(poll);
    handler.post(poll);
  }

  void stop() {
    polling = false;
    handler.removeCallbacks(poll);
  }

  private void check() {
    BuildResult result = BuildResult.read(context);
    if (result != null && result.isFinished() && !result.at.equals(acknowledged())) {
      try {
        listener.onBuildFinished(result);
        // Acknowledged only once the panel really took it, so a host that could not show the dialog
        // gets another chance on the next refresh.
        preferences.edit().putString(KEY_ACK, result.at).apply();
      } catch (RuntimeException ignored) {
        // The panel is gone; the next open will show this result again.
      }
      stop();
      return;
    }
    if (polling && SystemClock.elapsedRealtime() - startedAt < GIVE_UP_MS) {
      handler.postDelayed(poll, POLL_MS);
    } else {
      polling = false;
    }
  }

  private String acknowledged() {
    String value = preferences.getString(KEY_ACK, "");
    return value == null ? "" : value;
  }
}
