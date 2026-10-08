package ir.hanzodev30.nodeui;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NodeClient {

  private static final int TIMEOUT_MS = 12000;
  private static final int MAX_SUMMARY = 220;

  private NodeClient() {}

  static final class Package {
    final String name;
    final String version;
    final String summary;
    final String author;

    Package(String name, String version, String summary, String author) {
      this.name = name;
      this.version = version;
      this.summary = summary;
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

  static String baseName(String input) {
    if (input == null) {
      return "";
    }
    String name = input.trim();
    if (name.isEmpty()) {
      return "";
    }
    if (name.charAt(0) == '@') {
      int scoped = name.indexOf('@', 1);
      return scoped < 0 ? name : name.substring(0, scoped);
    }
    int cut = name.length();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '@' || c == '[' || c == '<' || c == '>' || c == '=' || c == '!' || c == '~'
          || c == ';' || c == ' ' || c == '\t' || c == '\n') {
        cut = i;
        break;
      }
    }
    return name.substring(0, cut).trim();
  }

  static Result lookup(Context context, String rawQuery) {
    String name = baseName(rawQuery);
    if (name.isEmpty()) {
      return Result.failed(context.getString(R.string.nodeui_error_type_package_name));
    }
    HttpURLConnection connection = null;
    try {
      URL url = new URL("https://registry.npmjs.org/" + encode(name) + "/latest");
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "GhostIDE-NodePackages");

      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_FOUND) {
        return Result.failed(context.getString(R.string.nodeui_error_no_project, name));
      }
      if (status != HttpURLConnection.HTTP_OK) {
        return Result.failed(context.getString(R.string.nodeui_error_http_status, status));
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

      JSONObject doc = new JSONObject(body.toString());
      String resolvedName = doc.optString("name", name);
      String version = doc.optString("version", "");
      String summary = collapse(doc.optString("description", ""));
      String author = authorOf(doc);
      return Result.found(new Package(resolvedName, version, summary, author));
    } catch (Exception e) {
      String message = e.getMessage();
      if (message == null || message.isEmpty()) {
        message = e.getClass().getSimpleName();
      }
      return Result.failed(context.getString(R.string.nodeui_error_network, message));
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String authorOf(JSONObject doc) {
    Object author = doc.opt("author");
    if (author instanceof JSONObject) {
      return collapse(((JSONObject) author).optString("name", ""));
    }
    if (author instanceof String) {
      return collapse((String) author);
    }
    return "";
  }

  private static String encode(String value) {
    try {
      return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
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
