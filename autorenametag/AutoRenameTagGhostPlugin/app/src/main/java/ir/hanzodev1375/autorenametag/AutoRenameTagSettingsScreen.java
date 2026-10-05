package ir.hanzodev1375.autorenametag;

import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ir.hanzodev1375.ghostide.ide.ui.api.PluginScreen;

public final class AutoRenameTagSettingsScreen implements PluginScreen {

  private final AutoRenameTagSettings settings;
  private final Context pluginContext;

  public AutoRenameTagSettingsScreen(AutoRenameTagSettings settings, Context pluginContext) {
    this.settings = settings;
    this.pluginContext = pluginContext;
  }

  @Override
  public String getId() {
    return "ir.hanzodev1375.autorenametag.settings";
  }

  @Override
  public String getTitle() {
    return pluginContext.getString(R.string.plugin_title);
  }

  @Override
  public Fragment createFragment() {
    return new SettingsFragment(settings, pluginContext);
  }

  public static final class SettingsFragment extends Fragment {

    private final AutoRenameTagSettings settings;
    private final Context pluginContext;

    public SettingsFragment(AutoRenameTagSettings settings, Context pluginContext) {
      this.settings = settings;
      this.pluginContext = pluginContext;
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
      Context themed = new ContextThemeWrapper(pluginContext, R.style.BaseThemeLight);
      LayoutInflater li = LayoutInflater.from(themed).cloneInContext(themed);
      View root = li.inflate(R.layout.settings_auto_rename_tag, container, false);
      LinearLayout rows = root.findViewById(R.id.rows);

      TextView header = (TextView) li.inflate(R.layout.text_header, rows, false);
      header.setText(text(R.string.header_behaviour));
      rows.addView(header);

      View toggle = li.inflate(R.layout.row_switch, rows, false);
      ((TextView) toggle.findViewById(R.id.row_title)).setText(text(R.string.pref_enabled_title));
      ((TextView) toggle.findViewById(R.id.row_desc)).setText(text(R.string.pref_enabled_desc));
      Switch enabled = toggle.findViewById(R.id.row_switch);
      enabled.setChecked(settings.enabled());
      enabled.setOnCheckedChangeListener((button, checked) -> settings.setEnabled(checked));
      rows.addView(toggle);

      View scope = li.inflate(R.layout.row_choice, rows, false);
      ((TextView) scope.findViewById(R.id.row_title)).setText(text(R.string.pref_scope_title));
      ((TextView) scope.findViewById(R.id.row_desc)).setText(text(R.string.pref_scope_desc));
      TextView value = scope.findViewById(R.id.row_value);
      value.setText(text(labelFor(settings.scope())));
      scope.setOnClickListener(
          view -> {
            AutoRenameTagSettings.Scope next = AutoRenameTagSettings.Scope.next(settings.scope());
            settings.setScope(next);
            value.setText(text(labelFor(next)));
          });
      rows.addView(scope);

      TextView note = (TextView) li.inflate(R.layout.text_note, rows, false);
      note.setText(text(R.string.note_about));
      rows.addView(note);

      return root;
    }

    private int labelFor(AutoRenameTagSettings.Scope scope) {
      switch (scope) {
        case HTML:
          return R.string.scope_html;
        case MARKUP:
          return R.string.scope_markup;
        default:
          return R.string.scope_templates;
      }
    }

    private String text(int resId) {
      return pluginContext.getString(resId);
    }
  }
}
