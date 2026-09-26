package ir.ghostide.pipui;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal PyPI client: resolves a package name through the official JSON API
 * ({@code https://pypi.org/pypi/<name>/json}) and reports what the install button will do.
 *
 * <p>Only the name is looked up; pip itself resolves the specifier, so {@code requests==2.31.0} and
 * {@code requests[security]} both work as long as the part before the first version/extras marker
 * is a real project name. Network calls happen on a background thread.
 */
final class PyPiClient {

  private static final int TIMEOUT_MS = 12000;
  private static final int MAX_SUMMARY = 220;

  private PyPiClient() {}

  static final class Package {
    final String name;
    final String version;
    final String summary;
    final String requiresPython;
    final String author;

    Package(String name, String version, String summary, String requiresPython, String author) {
      this.name = name;
      this.version = version;
      this.summary = summary;
      this.requiresPython = requiresPython;
      this.author = author;
    }
  }

  static final class Result {
    final Package pkg;
    final String error;

    private Result(Package pkg, String error) {
      this.pkg = pkg;
      this.error = error;
    }

    static Result found(Package pkg) {
      return new Result(pkg, null);
    }

    static Result failed(String error) {
      return new Result(null, error);
    }

    boolean isFound() {
      return pkg != null;
    }
  }

  /** Strips version pins, extras and environment markers so the remainder can hit the API. */
  static String baseName(String input) {
    if (input == null) {
      return "";
    }
    String name = input.trim();
    int cut = name.length();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '[' || c == '<' || c == '>' || c == '=' || c == '!' || c == '~' || c == ';'
          || c == '@' || c == ' ' || c == '\t' || c == '\n') {
        cut = i;
        break;
      }
    }
    return name.substring(0, cut).trim();
  }

  static Result lookup(String rawQuery) {
    String name = baseName(rawQuery);
    if (name.isEmpty()) {
      return Result.failed("Type a package name first");
    }
    HttpURLConnection connection = null;
    try {
      URL url = new URL("https://pypi.org/pypi/" + encode(name) + "/json");
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "GhostIDE-PipInstaller");

      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_FOUND) {
        return Result.failed("No project named '" + name + "' on PyPI");
      }
      if (status != HttpURLConnection.HTTP_OK) {
        return Result.failed("PyPI answered HTTP " + status);
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

      JSONObject info = new JSONObject(body.toString()).getJSONObject("info");
      String resolvedName = info.optString("name", name);
      String version = info.optString("version", "");
      String summary = collapse(info.optString("summary", ""));
      String requiresPython = info.optString("requires_python", "");
      String author = info.optString("author", "");
      if (author.isEmpty()) {
        author = info.optString("maintainer", "");
      }
      return Result.found(
          new Package(resolvedName, version, summary, requiresPython.trim(), collapse(author)));
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

  private static String encode(String value) {
    try {
      return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
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
