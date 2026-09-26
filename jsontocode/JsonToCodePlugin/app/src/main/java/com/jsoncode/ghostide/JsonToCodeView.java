package com.jsoncode.ghostide;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import ir.hanzodev1375.ghostide.ide.ui.api.EditorHost;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JsonToCodeView {

  private static final ExecutorService GENERATOR = Executors.newSingleThreadExecutor();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private final Context context;
  private final JsonToCodePlugin owner;
  private final EditorHost editorHost;
  private final View root;

  private Spinner languageSpinner;
  private EditText classNameInput;
  private Button generateButton;
  private TextView status;
  private String selectedJson;

  public JsonToCodeView(Context context, JsonToCodePlugin owner, EditorHost editorHost) {
    this.context = new ContextThemeWrapper(context, android.R.style.ThemeOverlay_Material_Dark_ActionBar);
    this.owner = owner;
    this.editorHost = editorHost;
    this.root = build();
    root.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View view) {
            // A long press capture wins over the live selection, otherwise fall back to it.
            if (!owner.attachView(JsonToCodeView.this)) {
              loadCurrentSelection();
            }
          }

          @Override
          public void onViewDetachedFromWindow(View view) {
            owner.detachView(JsonToCodeView.this);
          }
        });
  }

  public View getRoot() {
    return root;
  }

  private View build() {
    View root = LayoutInflater.from(context).inflate(R.layout.json_to_code_view, null, false);
    languageSpinner = root.findViewById(R.id.json_language);
    classNameInput = root.findViewById(R.id.json_class_name);
    generateButton = root.findViewById(R.id.json_generate);
    status = root.findViewById(R.id.json_status);

    JsonCodeGenerator.Language[] languages = JsonCodeGenerator.Language.values();
    String[] labels = new String[languages.length];
    for (int i = 0; i < languages.length; i++) {
      labels[i] = languages[i].getLabel();
    }
    languageSpinner.setAdapter(
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
    generateButton.setOnClickListener(view -> generateSelected());
    root.findViewById(R.id.json_use_selection).setOnClickListener(view -> loadCurrentSelection());
    return root;
  }

  /** Pulls whatever the user currently has selected in the editor. */
  void loadCurrentSelection() {
    String json = SelectedTextReader.read(editorHost);
    if (json.isEmpty()) {
      showNoSelection();
      return;
    }
    applyJson(json);
  }

  /** Entry point used by the long press listener once a JSON block has been resolved. */
  void acceptJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      showNoSelection();
      return;
    }
    applyJson(json);
  }

  private void applyJson(String json) {
    if (!JsonCodeGenerator.isValidJson(json)) {
      selectedJson = null;
      generateButton.setEnabled(false);
      classNameInput.setError(null);
      status.setText("That selection is not valid JSON.");
      return;
    }
    selectedJson = json;
    generateButton.setEnabled(true);
    classNameInput.setError(null);
    suggestClassName();
    status.setText("Selection ready (" + json.length() + " chars).");
  }

  private void suggestClassName() {
    File openFile = editorHost == null ? null : editorHost.getOpenFile();
    String suggested =
        openFile == null
            ? "GeneratedModel"
            : JsonCodeGenerator.suggestedClassName(openFile.getName());
    if (suggested == null || suggested.isEmpty()) {
      suggested = "GeneratedModel";
    }
    classNameInput.setText(suggested);
    classNameInput.setSelection(classNameInput.length());
  }

  private void showNoSelection() {
    selectedJson = null;
    generateButton.setEnabled(false);
    classNameInput.setError(null);
    status.setText("Nothing selected. Select JSON text in the editor, or long press it.");
  }

  private void generateSelected() {
    String json = selectedJson;
    if (json == null) {
      showNoSelection();
      return;
    }

    String className = classNameInput.getText().toString().trim();
    if (!JsonCodeGenerator.isValidClassName(className)) {
      classNameInput.setError("Enter a valid class name");
      return;
    }

    JsonCodeGenerator.Language[] languages = JsonCodeGenerator.Language.values();
    int selectedPosition = languageSpinner.getSelectedItemPosition();
    if (selectedPosition < 0 || selectedPosition >= languages.length) {
      status.setText("Select a language.");
      return;
    }

    status.setText("Generating...");
    generate(context, editorHost, json, className, languages[selectedPosition], status);
  }

  private static void generate(
      Context context,
      EditorHost host,
      String json,
      String className,
      JsonCodeGenerator.Language language,
      TextView status) {
    if (host == null) {
      setStatus(status, "No editor is available.");
      return;
    }

    File projectRoot = host.getProjectRoot();
    if (projectRoot == null) {
      setStatus(status, "Open a project before generating a file.");
      return;
    }

    GENERATOR.execute(
        () -> {
          try {
            JsonCodeGenerator.GeneratedCode generated =
                JsonCodeGenerator.generate(json, className, language);
            if (!projectRoot.exists() && !projectRoot.mkdirs()) {
              throw new IllegalStateException("Cannot create project directory");
            }
            File output = new File(projectRoot, generated.getFileName());
            try (FileOutputStream stream = new FileOutputStream(output)) {
              stream.write(generated.getText().getBytes(StandardCharsets.UTF_8));
            }
            MAIN.post(
                () -> {
                  try {
                    host.openFile(output);
                    if (context != null) {
                      Toast.makeText(
                              context, "Generated " + output.getAbsolutePath(), Toast.LENGTH_LONG)
                          .show();
                    }
                    setStatus(status, "Generated " + output.getName());
                  } catch (Exception exception) {
                    setStatus(status, errorMessage(exception));
                  }
                });
          } catch (Exception exception) {
            MAIN.post(() -> setStatus(status, errorMessage(exception)));
          }
        });
  }

  private static void setStatus(TextView status, String message) {
    if (status != null) {
      status.setText(message);
    }
  }

  private static String errorMessage(Exception exception) {
    return exception.getMessage() == null
        ? "Could not generate the source file"
        : exception.getMessage();
  }
}
