package com.opencode.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.opencode.chat.client.OpenCodeClient;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;

/**
 * The chat UI, built 100% programmatically (no resources) and returned from {@link
 * EditorPanel#createView()} so it lives inside the editor screen. Talks to an {@code opencode
 * serve} server on a background executor and demos the GhostIDE {@link EditorHost}: attach the
 * open file, insert the assistant reply into the editor.
 */
public final class OpenCodeChatView {

  private static final int COLOR_BG = 0xFF0F1214;
  private static final int COLOR_USER = 0xFF1E3A5F;
  private static final int COLOR_ASSISTANT = 0xFF1B3A2B;
  private static final int COLOR_ERROR = 0xFF7A1F1F;
  private static final int COLOR_MUTED = 0xFF90A4AE;

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private LinearLayout messages;
  private ScrollView scroll;
  private TextView status;
  private EditText urlInput;
  private EditText promptInput;

  private OpenCodeClient client;
  private String sessionId;
  private String lastReply;

  private final LinearLayout root;

  public OpenCodeChatView(Context context) {
    this.context = context;
    this.root = build();
  }

  public View getRoot() {
    return root;
  }

  private LinearLayout build() {
    PluginContext plugin = OpenCodeChatPlugin.getPluginContext();

    LinearLayout root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(COLOR_BG);
    root.setPadding(dp(12), dp(12), dp(12), dp(12));

    root.addView(
        tag(context, "Demo: opencode.ai HTTP API + GhostIDE EditorPanel / EditorHost / PluginLogger", COLOR_MUTED, 11));

    // ---- server row -----------------------------------------------------
    LinearLayout serverRow = new LinearLayout(context);
    serverRow.setOrientation(LinearLayout.HORIZONTAL);
    serverRow.setGravity(Gravity.CENTER_VERTICAL);
    serverRow.addView(label(context, "server", COLOR_MUTED));

    urlInput = new EditText(context);
    urlInput.setText("http://127.0.0.1:4096");
    urlInput.setSingleLine(true);
    urlInput.setTextSize(13);
    urlInput.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
    urlLp.setMargins(dp(6), 0, dp(6), 0);
    serverRow.addView(urlInput, urlLp);

    Button checkBtn = new Button(context);
    checkBtn.setText("Check");
    checkBtn.setOnClickListener(v -> checkServer());
    serverRow.addView(checkBtn, new LinearLayout.LayoutParams(WRAP, dp(44)));

    Button newBtn = new Button(context);
    newBtn.setText("New session");
    newBtn.setOnClickListener(v -> newSession());
    serverRow.addView(newBtn, new LinearLayout.LayoutParams(WRAP, dp(44)));

    root.addView(serverRow);

    // ---- status row -----------------------------------------------------
    status = new TextView(context);
    status.setText("not connected — tap Check");
    status.setTextColor(COLOR_MUTED);
    status.setTextSize(12);
    status.setPadding(0, dp(4), 0, dp(4));
    root.addView(status);

    // ---- messages -------------------------------------------------------
    messages = new LinearLayout(context);
    messages.setOrientation(LinearLayout.VERTICAL);

    scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    scroll.addView(messages, new ScrollView.LayoutParams(MATCH, WRAP));
    LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
    scrollLp.setMargins(0, dp(4), 0, dp(4));
    root.addView(scroll, scrollLp);

    // ---- quick actions --------------------------------------------------
    LinearLayout actionRow = new LinearLayout(context);
    actionRow.setOrientation(LinearLayout.HORIZONTAL);
    Button attachBtn = new Button(context);
    attachBtn.setText("Attach editor file");
    attachBtn.setOnClickListener(v -> attachEditor());
    actionRow.addView(attachBtn, new LinearLayout.LayoutParams(0, dp(42), 1f));
    Button insertBtn = new Button(context);
    insertBtn.setText("Reply → editor");
    insertBtn.setOnClickListener(v -> insertIntoEditor());
    actionRow.addView(insertBtn, new LinearLayout.LayoutParams(0, dp(42), 1f));
    root.addView(actionRow);

    // ---- input row ------------------------------------------------------
    LinearLayout inputRow = new LinearLayout(context);
    inputRow.setOrientation(LinearLayout.HORIZONTAL);
    inputRow.setGravity(Gravity.BOTTOM);
    promptInput = new EditText(context);
    promptInput.setHint("Ask opencode…");
    promptInput.setHintTextColor(COLOR_MUTED);
    promptInput.setTextColor(Color.WHITE);
    promptInput.setMinLines(1);
    promptInput.setMaxLines(5);
    LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
    promptLp.setMargins(0, 0, dp(6), 0);
    inputRow.addView(promptInput, promptLp);
    Button sendBtn = new Button(context);
    sendBtn.setText("Send");
    sendBtn.setOnClickListener(v -> send());
    inputRow.addView(sendBtn, new LinearLayout.LayoutParams(WRAP, dp(48)));
    root.addView(inputRow);

    if (plugin == null) {
      addError("Plugin context unavailable (plugin not activated).");
    }
    return root;
  }

  // ---- actions ----------------------------------------------------------

  private void send() {
    final String text = promptInput.getText().toString().trim();
    if (text.isEmpty()) {
      return;
    }
    promptInput.setText("");
    addMessage("You", text, COLOR_USER);
    final String url = currentUrl();
    setStatus("waiting for opencode…");
    executor.execute(
        () -> {
          try {
            ensureClient(url);
            OpenCodeClient.Reply reply = client.sendMessage(sessionId, text);
            lastReply = reply.text;
            ui(
                () -> {
                  addMessage("assistant", reply.text, COLOR_ASSISTANT);
                  setStatus("session " + reply.sessionId + " · message " + reply.messageId);
                });
          } catch (Exception e) {
            ui(() -> addError("Request failed: " + e.getMessage()));
          }
        });
  }

  private void checkServer() {
    final String url = currentUrl();
    setStatus("checking…");
    executor.execute(
        () -> {
          try {
            OpenCodeClient c = new OpenCodeClient(url);
            String version = c.health();
            StringBuilder agents = new StringBuilder();
            for (OpenCodeClient.Agent a : c.listAgents()) {
              if (agents.length() > 0) {
                agents.append(", ");
              }
              agents.append(a);
            }
            client = c;
            final String agentList = agents.length() == 0 ? "(none)" : agents.toString();
            final String ver = version;
            ui(
                () -> {
                  setStatus("connected: opencode v" + ver);
                  addMessage(
                      "opencode",
                      "Connected to " + c.base + " (v" + ver + ").\nAgents: " + agentList,
                      COLOR_ASSISTANT);
                });
          } catch (Exception e) {
            ui(() -> addError("Cannot reach " + url + ": " + e.getMessage()));
          }
        });
  }

  private void newSession() {
    final String url = currentUrl();
    setStatus("creating session…");
    executor.execute(
        () -> {
          try {
            OpenCodeClient c = new OpenCodeClient(url);
            final String id = c.createSession("GhostIDE chat");
            client = c;
            sessionId = id;
            ui(() -> setStatus("session " + id + " ready"));
          } catch (Exception e) {
            ui(() -> addError("New session failed: " + e.getMessage()));
          }
        });
  }

  private void attachEditor() {
    PluginContext plugin = OpenCodeChatPlugin.getPluginContext();
    if (plugin == null) {
      addError("Plugin context unavailable.");
      return;
    }
    EditorHost host = plugin.getServices().require(IdeHostServices.EDITOR_HOST);
    File open = host.getOpenFile();
    String text = host.getEditorText();
    String path = open != null ? open.getAbsolutePath() : "(no open file)";
    String block = "```\n" + path + "\n" + text + "\n```";
    String cur = promptInput.getText().toString();
    promptInput.setText(cur.isEmpty() ? block : cur + "\n" + block);
    promptInput.setSelection(promptInput.getText().length());
    setStatus("attached " + path);
  }

  private void insertIntoEditor() {
    if (lastReply == null) {
      addError("No assistant reply yet.");
      return;
    }
    PluginContext plugin = OpenCodeChatPlugin.getPluginContext();
    if (plugin == null) {
      addError("Plugin context unavailable.");
      return;
    }
    EditorHost host = plugin.getServices().require(IdeHostServices.EDITOR_HOST);
    host.setEditorText(lastReply);
    setStatus("Inserted reply into the open editor");
  }

  // ---- helpers ----------------------------------------------------------

  private void ensureClient(String url) throws Exception {
    if (client == null || !client.base.equals(normalize(url))) {
      client = new OpenCodeClient(url);
    }
    if (sessionId == null) {
      sessionId = client.createSession("GhostIDE chat");
    }
  }

  private String currentUrl() {
    return urlInput.getText().toString().trim();
  }

  private static String normalize(String url) {
    String b = url == null ? "" : url.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return b.isEmpty() ? "http://127.0.0.1:4096" : b;
  }

  private void setStatus(final String text) {
    ui(() -> status.setText(text));
  }

  private void addMessage(String who, String text, int color) {
    TextView tv = new TextView(context);
    tv.setText((who.isEmpty() ? "" : who + ": ") + text);
    tv.setTextColor(Color.WHITE);
    tv.setTextSize(14);
    tv.setPadding(dp(12), dp(8), dp(12), dp(8));
    tv.setLineSpacing(0f, 1.1f);

    GradientDrawable bubble = new GradientDrawable();
    bubble.setColor(color);
    bubble.setCornerRadius(dp(12));
    tv.setBackground(bubble);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
    lp.setMargins(0, dp(4), 0, dp(4));
    messages.addView(tv, lp);
    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
  }

  private void addError(final String text) {
    TextView tv = new TextView(context);
    tv.setText(text);
    tv.setTextColor(Color.WHITE);
    tv.setTextSize(13);
    tv.setPadding(dp(10), dp(6), dp(10), dp(6));
    GradientDrawable bubble = new GradientDrawable();
    bubble.setColor(COLOR_ERROR);
    bubble.setCornerRadius(dp(10));
    tv.setBackground(bubble);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
    lp.setMargins(0, dp(4), 0, dp(4));
    messages.addView(tv, lp);
    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
  }

  private static TextView label(Context ctx, String text, int color) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextColor(color);
    tv.setTextSize(12);
    return tv;
  }

  private static TextView tag(Context ctx, String text, int color, int sp) {
    TextView tv = label(ctx, text, color);
    tv.setTextSize(sp);
    tv.setPadding(0, dp(ctx, 2), 0, dp(ctx, 6));
    return tv;
  }

  private void ui(Runnable r) {
    context.getMainExecutor().execute(r);
  }

  private int dp(int value) {
    return dp(context, value);
  }

  private static int dp(Context ctx, int value) {
    return Math.round(value * ctx.getResources().getDisplayMetrics().density);
  }

  private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
  private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
