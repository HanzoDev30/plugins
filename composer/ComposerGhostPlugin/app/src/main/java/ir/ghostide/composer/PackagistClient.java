package ir.ghostide.composer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Packagist client: resolves a package through the official JSON API
 * ({@code https://repo.packagist.org/p2/<vendor>/<name>.json}) and reports what the install button
 * is about to do.
 *
 * <p>Only the package name is looked up; Composer itself resolves the version constraint, so {@code
 * guzzlehttp/guzzle:^7.0} and {@code laravel/framework} both work as long as the part before the
 * version marker is a real package name. Network calls happen on a background thread.
 */
final class PackagistClient {

  private static final int TIMEOUT_MS = 12000;
  private static final int MAX_SUMMARY = 240;

  private PackagistClient() {}

  static final class PackagistPackage {
    final String name;
    final String version;
    final String summary;
    final String downloads;
    final String license;
    final String requiresPhp;

    PackagistPackage(
        String name, String version, String summary, String downloads, String license,
        String requiresPhp) {
      this.name = name;
      this.version = version;
      this.summary = summary;
      this.downloads = downloads;
      this.license = license;
      this.requiresPhp = requiresPhp;
    }
  }

  static final class Result {
    final PackagistPackage pkg;
    final String error;

    private Result(PackagistPackage pkg, String error) {
      this.pkg = pkg;
      this.error = error;
    }

    static Result found(PackagistPackage pkg) {
      return new Result(pkg, null);
    }

    static Result failed(String error) {
      return new Result(null, error);
    }

    boolean isFound() {
      return pkg != null;
    }
  }

  /** Strips a version constraint or a {@code @dev} branch so the remainder can hit the API. */
  static String baseName(String input) {
    if (input == null) {
      return "";
    }
    String name = input.trim();
    int cut = name.length();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == ':' || c == '@' || c == ' ' || c == '\t' || c == '\n') {
        cut = i;
        break;
      }
    }
    return name.substring(0, cut).trim();
  }

  /** Rebuilds {@code vendor/name} plus whatever constraint the user typed after the colon. */
  static String requirement(String typed, String resolvedName) {
    String raw = typed == null ? "" : typed.trim();
    String base = baseName(raw);
    if (!base.isEmpty() && raw.length() > base.length()) {
      return resolvedName + raw.substring(base.length());
    }
    return resolvedName;
  }

  static Result lookup(String rawQuery) {
    String name = baseName(rawQuery);
    if (name.isEmpty()) {
      return Result.failed("type a package name first");
    }
    if (!name.contains("/")) {
      return Result.failed("a Composer package is written as vendor/name");
    }

    HttpURLConnection connection = null;
    try {
      URL url = new URL("https://repo.packagist.org/p2/" + encode(name) + ".json");
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "GhostIDE-Composer");

      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_FOUND) {
        return Result.failed("no package named '" + name + "' on Packagist");
      }
      if (status != HttpURLConnection.HTTP_OK) {
        return Result.failed("Packagist answered HTTP " + status);
      }

      StringBuilder body = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
        }
      }

      JSONObject root = new JSONObject(body.toString());
      JSONArray packages = root.optJSONArray("packages");
      if (packages == null || packages.length() == 0) {
        return Result.failed("no package named '" + name + "' on Packagist");
      }
      JSONObject best = packages.optJSONObject(0);
      if (best == null) {
        return Result.failed("no package named '" + name + "' on Packagist");
      }

      String resolvedName = name;
      JSONArray names = best.optJSONArray("name");
      if (names != null && names.length() > 0) {
        resolvedName = names.optString(0, name);
      }
      String version = best.optString("version", "");
      String summary = collapse(best.optString("description", ""));
      String requiresPhp = best.optString("require", "").isEmpty() ? "" : "";
      JSONObject require = best.optJSONObject("require");
      if (require != null) {
        requiresPhp = require.optString("php", "");
      }
      String license = "";
      JSONArray licenses = best.optJSONArray("license");
      if (licenses != null && licenses.length() > 0) {
        license = collapse(licenses.optString(0, ""));
      }
      return Result.found(
          new PackagistPackage(
              resolvedName, version, summary, formatDownloads(name), license, requiresPhp.trim()));
    } catch (Exception e) {
      String message = e.getMessage();
      return Result.failed(
          message == null || message.isEmpty() ? e.getClass().getSimpleName() : message);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String formatDownloads(String name) {
    HttpURLConnection connection = null;
    try {
      URL url =
          new URL("https://packagist.org/packages/" + encode(name) + ".json");
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "GhostIDE-Composer");
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return "";
      }
      StringBuilder body = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
        }
      }
      JSONObject packageInfo = new JSONObject(body.toString()).optJSONObject("package");
      if (packageInfo == null) {
        return "";
      }
      long total = packageInfo.optLong("downloads", 0L);
      if (total <= 0) {
        return "";
      }
      if (total >= 1_000_000L) {
        return String.format(java.util.Locale.US, "%.1fM", total / 1_000_000.0);
      }
      if (total >= 1_000L) {
        return String.format(java.util.Locale.US, "%.1fK", total / 1_000.0);
      }
      return String.valueOf(total);
    } catch (Exception e) {
      return "";
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    } catch (Exception e) {
      return value;
    }
  }

  private static String collapse(String value) {
    if (value == null) {
      return "";
    }
    String flat = value.replaceAll("\\s+", " ").trim();
    if (flat.length() > MAX_SUMMARY) {
      return flat.substring(0, MAX_SUMMARY - 1) + "\u2026";
    }
    return flat;
  }
}
