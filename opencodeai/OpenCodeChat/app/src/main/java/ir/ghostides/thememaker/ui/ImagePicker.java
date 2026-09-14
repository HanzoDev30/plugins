package ir.ghostides.thememaker.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Picks an image from the gallery.
 *
 * <p>The plugin's UI is a {@code View} inside the host's editor screen, so it cannot use {@code
 * startActivityForResult} on its own. Instead the current resumed {@link Activity} is tracked via
 * an {@link Application.ActivityLifecycleCallbacks} hook, and the picker is launched through that
 * activity's {@link ActivityResultRegistry} (the host's activities are {@code AppCompatActivity},
 * i.e. {@code ComponentActivity}s, which expose it). The system document picker (SAF) is used so
 * no storage permission is needed and every Android version is supported.
 */
public final class ImagePicker {

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private static volatile Activity currentActivity;

  private ImagePicker() {}

  public static void trackActivities(Context context) {
    Context app = context.getApplicationContext();
    if (app instanceof Application) {
      ((Application) app)
          .registerActivityLifecycleCallbacks(
              new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                  currentActivity = activity;
                }

                @Override
                public void onActivityPaused(Activity activity) {
                  if (currentActivity == activity) {
                    currentActivity = null;
                  }
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityStopped(Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                @Override
                public void onActivityDestroyed(Activity activity) {}
              });
    }
  }

  public static Activity getCurrentActivity() {
    return currentActivity;
  }

  /** Launches the document picker. The callback is invoked on the main thread with the URI. */
  public static void pick(Context context, PickerCallback callback) {
    Activity activity = currentActivity;
    if (activity == null) {
      callback.onError("No foreground activity to host the image picker.");
      return;
    }
    if (!(activity instanceof ComponentActivity)) {
      callback.onError("Host activity does not support the activity-result API.");
      return;
    }

    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("image/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/png", "image/jpeg", "image/webp", "image/gif"});

    String key = "thememaker_pick_" + COUNTER.incrementAndGet();
    ActivityResultRegistry registry = ((ComponentActivity) activity).getActivityResultRegistry();
    ActivityResultContracts.StartActivityForResult contract = new ActivityResultContracts.StartActivityForResult();

    registry.register(
        key,
        activity,
        contract,
        result -> {
          registry.unregister(key);
          if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            callback.onPicked(result.getData().getData());
          } else {
            callback.onCancel();
          }
        });

    registry.launch(key, contract.createIntent(context, intent), 0);
  }

  /** Decodes a picked image, down-sampled to at most {@code maxPixels} on the longest side. */
  public static Bitmap decodeBitmap(Context context, Uri uri, int maxPixels) {
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      if (in == null) {
        return null;
      }
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null;
      }
      int sample = 1;
      int longest = Math.max(bounds.outWidth, bounds.outHeight);
      while (longest / (sample * 2) >= maxPixels) {
        sample *= 2;
      }
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = sample;
      try (InputStream in2 = context.getContentResolver().openInputStream(uri)) {
        return in2 == null ? null : BitmapFactory.decodeStream(in2, null, opts);
      }
    } catch (Exception e) {
      return null;
    }
  }

  public interface PickerCallback {
    void onPicked(Uri uri);

    default void onCancel() {}

    default void onError(String message) {}
  }
}
