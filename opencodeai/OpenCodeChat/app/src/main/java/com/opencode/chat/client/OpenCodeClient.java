package com.opencode.chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal HTTP client for the opencode server API ({@code opencode serve}), see
 * https://opencode.ai/docs/server/. Plain {@link HttpURLConnection} + {@code org.json}, no extra
 * dependencies. Implements: GET /global/health, GET /agent, POST /session,
 * POST /session/:id/message.
 */
public final class OpenCodeClient {

  private static final String DEFAULT_BASE = "http://127.0.0.1:4096";
  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 300_000;

  public final String base;

  public OpenCodeClient(String baseUrl) {
    String b = baseUrl == null ? "" : baseUrl.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    this.base = b.isEmpty() ? DEFAULT_BASE : b;
  }

  public static final class Agent {
    public final String id;
    public final String name;

    Agent(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String toString() {
      return name == null || name.isEmpty() ? id : name;
    }
  }

  public static final class Reply {
    public final String sessionId;
    public final String messageId;
    public final String role;
    public final String text;

    Reply(String sessionId, String messageId, String role, String text) {
      this.sessionId = sessionId;
      this.messageId = messageId;
      this.role = role;
      this.text = text;
    }
  }

  public String health() throws Exception {
    JSONObject body = new JSONObject(request("GET", "/global/health", null));
    return body.optString("version", "unknown");
  }

  public List<Agent> listAgents() throws Exception {
    List<Agent> out = new ArrayList<>();
    JSONArray arr = new JSONArray(request("GET", "/agent", null));
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o != null) {
        out.add(new Agent(o.optString("id", "?"), o.optString("name", "")));
      }
    }
    return out;
  }

  public String createSession(String title) throws Exception {
    JSONObject body = new JSONObject();
    if (title != null && !title.isEmpty()) {
      body.put("title", title);
    }
    JSONObject o = new JSONObject(request("POST", "/session", body.toString()));
    String id = o.optString("id", null);
    if (id == null || id.isEmpty()) {
      throw new IOException("No session id in response: " + o);
    }
    return id;
  }

  public Reply sendMessage(String sessionId, String message) throws Exception {
    JSONObject part = new JSONObject();
    part.put("type", "text");
    part.put("text", message);
    part.put("sessionID", sessionId);

    JSONObject body = new JSONObject();
    body.put("sessionID", sessionId);
    body.put("parts", new JSONArray().put(part));

    String path = "/session/" + encode(sessionId) + "/message";
    JSONObject root = new JSONObject(request("POST", path, body.toString()));

    JSONObject info = root.optJSONObject("info");
    String messageId = info != null ? info.optString("id", "") : "";
    String role = info != null ? info.optString("role", "assistant") : "assistant";

    StringBuilder text = new StringBuilder();
    JSONArray parts = root.optJSONArray("parts");
    if (parts != null) {
      for (int i = 0; i < parts.length(); i++) {
        JSONObject p = parts.optJSONObject(i);
        if (p == null) {
          continue;
        }
        if ("text".equals(p.optString("type"))) {
          String t = p.optString("text", "");
          if (!t.isEmpty()) {
            if (text.length() > 0) {
              text.append('\n');
            }
            text.append(t);
          }
        }
      }
    }
    String replyText = text.length() == 0 ? "(no text reply)" : text.toString();
    return new Reply(sessionId, messageId, role, replyText);
  }

  private String request(String method, String path, String body) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
    try {
      conn.setRequestMethod(method);
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setUseCaches(false);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("Content-Type", "application/json");
      if (body != null) {
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
          os.write(body.getBytes(StandardCharsets.UTF_8));
        }
      }
      int code = conn.getResponseCode();
      InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
      String text = readAll(in);
      if (code >= 300) {
        throw new IOException("HTTP " + code + " " + path + ": " + text);
      }
      return text;
    } finally {
      conn.disconnect();
    }
  }

  private static String readAll(InputStream in) throws IOException {
    if (in == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString().trim();
  }

  private static String encode(String value) {
    return value.replace(" ", "%20");
  }
}
