package ir.ghostide.composerlsp.checker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Looks up the newest stable release of a Composer package on Packagist.
 *
 * <p>Uses the metadata v2 endpoint ({@code https://repo.packagist.org/p2/<vendor>/<name>.json}),
 * which answers with every published version, newest first. Requests run on a background executor
 * so the editor thread is never blocked, and the callback is therefore invoked off the main thread
 * — callers must hop back with {@code editor.post(...)} before touching the editor.
 *
 * <p>The newest <em>stable</em> release wins: {@code dev-*}, {@code *-dev} and any pre-release
 * (alpha/beta/RC, i.e. a version carrying a {@code -} suffix) are skipped, so a project sitting on
 * {@code ^1.0} is not told to move to {@code 2.0-beta1}.
 */
public final class PackagistVersionChecker {

  public interface Callback {
    /** @param newest newest stable version, or null when the lookup failed / found nothing. */
    void onResult(String newest);
  }

  private static final int TIMEOUT_MS = 8000;
  private static final String USER_AGENT = "GhostIde-ComposerLsp-Plugin";
  private static final String ENDPOINT = "https://repo.packagist.org/p2/";

  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
  private static final OkHttpClient CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
          .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
          .callTimeout(TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS)
          .build();

  private PackagistVersionChecker() {}

  /** Looks up the newest stable version of {@code vendor/name} and reports it on the callback. */
  public static void check(String vendor, String name, Callback callback) {
    if (vendor == null || name == null || vendor.isEmpty() || name.isEmpty()) {
      deliver(callback, null);
      return;
    }
    EXECUTOR.execute(
        () -> {
          String found = query(vendor, name);
          deliver(callback, found);
        });
  }

  private static void deliver(Callback callback, String newest) {
    if (callback != null) {
      callback.onResult(newest);
    }
  }

  private static String query(String vendor, String name) {
    String url = ENDPOINT + vendor + "/" + name + ".json";
    Request request =
        new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build();
    try (Response response = CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        return null;
      }
      return parse(response.body().string(), vendor + "/" + name);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static String parse(String body, String key) {
    try {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      JsonObject packages = root.getAsJsonObject("packages");
      if (packages == null) {
        return null;
      }
      JsonArray releases = releasesFor(packages, key);
      if (releases == null || releases.size() == 0) {
        return null;
      }
      // Packagist sorts newest first, so the first stable entry is the newest stable release.
      String fallback = null;
      for (JsonElement element : releases) {
        if (element == null || !element.isJsonObject()) {
          continue;
        }
        JsonObject release = element.getAsJsonObject();
        JsonElement version = release.get("version");
        if (version == null || version.isJsonNull()) {
          continue;
        }
        String value = version.getAsString();
        if (value == null || value.isEmpty()) {
          continue;
        }
        if (fallback == null) {
          fallback = value;
        }
        if (isStable(value)) {
          return value;
        }
      }
      // Only dev branches exist for this package; better a branch name than nothing at all.
      return fallback;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Package keys are lower-cased by Packagist, so an exact match may miss by case alone. */
  private static JsonArray releasesFor(JsonObject packages, String key) {
    JsonArray exact = packages.getAsJsonArray(key);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, JsonElement> entry : packages.entrySet()) {
      if (key.equalsIgnoreCase(entry.getKey())) {
        return packages.getAsJsonArray(entry.getKey());
      }
    }
    return null;
  }

  private static boolean isStable(String version) {
    if (version.startsWith("dev-") || version.endsWith("-dev")) {
      return false;
    }
    if (version.indexOf('-') >= 0) {
      return false;
    }
    return ComposerConstraint.isComparable(version);
  }
}
