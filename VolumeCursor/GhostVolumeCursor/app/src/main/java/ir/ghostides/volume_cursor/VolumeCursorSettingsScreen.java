package ir.ghostides.volume_cursor;

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

import java.util.function.Consumer;
import java.util.function.Supplier;

import ir.hanzodev1375.ghostide.ide.ui.api.PluginScreen;

public final class VolumeCursorSettingsScreen implements PluginScreen {

  private static final String[] DIRECTIONS = {
    "horizontal", "horizontal_reverse", "vertical", "vertical_reverse"
  };
  private static final int[] DIRECTION_LABELS = {
    R.string.direction_horizontal,
    R.string.direction_horizontal_reverse,
    R.string.direction_vertical,
    R.string.direction_vertical_reverse
  };
  private static final String[] MOVEMENTS = {"character", "word"};
  private static final int[] MOVEMENT_LABELS = {
    R.string.movement_character, R.string.movement_word
  };
  private static final String[] ACTIONS = {"none", "word", "all"};
  private static final int[] ACTION_LABELS = {
    R.string.action_none, R.string.action_word, R.string.action_all
  };

  private final VolumeCursorSettings settings;
  private final Context pluginContext;

  public VolumeCursorSettingsScreen(VolumeCursorSettings settings, Context pluginContext) {
    this.settings = settings;
    this.pluginContext = pluginContext;
  }

  @Override
  public String getId() {
    return "ir.ghostides.volume_cursor.settings";
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

    private final VolumeCursorSettings settings;
    private final Context pluginContext;

    public SettingsFragment(VolumeCursorSettings settings, Context pluginContext) {
      this.settings = settings;
      this.pluginContext = pluginContext;
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
      Context themed = new ContextThemeWrapper(pluginContext, R.style.BaseThemeLight);
      LayoutInflater li = LayoutInflater.from(themed).cloneInContext(themed);
      View root = li.inflate(R.layout.settings_volume_cursor, container, false);
      LinearLayout rows = root.findViewById(R.id.rows);

      addHeader(li, rows, R.string.header_move_cursor);
      addChoice(
          li,
          rows,
          R.string.pref_direction_title,
          R.string.pref_direction_desc,
          DIRECTIONS,
          DIRECTION_LABELS,
          settings::cursorDirection,
          settings::setCursorDirection);
      addChoice(
          li,
          rows,
          R.string.pref_movement_title,
          R.string.pref_movement_desc,
          MOVEMENTS,
          MOVEMENT_LABELS,
          settings::movementMode,
          settings::setMovementMode);
      addSwitch(
          li,
          rows,
          R.string.pref_wrap_title,
          R.string.pref_wrap_desc,
          settings.wrapLines(),
          settings::setWrapLines);
      addSwitch(
          li,
          rows,
          R.string.pref_only_keyboard_title,
          R.string.pref_only_keyboard_desc,
          settings.onlyKeyboardVisible(),
          settings::setOnlyKeyboardVisible);
      addSwitch(
          li,
          rows,
          R.string.pref_haptic_title,
          R.string.pref_haptic_desc,
          settings.hapticFeedback(),
          settings::setHapticFeedback);

      addHeader(li, rows, R.string.header_select_two_buttons);
      addChoice(
          li,
          rows,
          R.string.pref_both_title,
          R.string.pref_both_desc,
          ACTIONS,
          ACTION_LABELS,
          settings::bothButtonAction,
          settings::setBothButtonAction);

      return root;
    }

    private String text(int resId) {
      return pluginContext.getString(resId);
    }

    private void addHeader(LayoutInflater li, LinearLayout rows, int headerRes) {
      TextView header = (TextView) li.inflate(R.layout.text_header, rows, false);
      header.setText(text(headerRes));
      rows.addView(header);
    }

    private void addSwitch(
        LayoutInflater li,
        LinearLayout rows,
        int titleRes,
        int descRes,
        boolean initial,
        Consumer<Boolean> setter) {
      View row = li.inflate(R.layout.row_switch, rows, false);
      ((TextView) row.findViewById(R.id.row_title)).setText(text(titleRes));
      ((TextView) row.findViewById(R.id.row_desc)).setText(text(descRes));
      Switch toggle = row.findViewById(R.id.row_switch);
      toggle.setChecked(initial);
      toggle.setOnCheckedChangeListener((button, isChecked) -> setter.accept(isChecked));
      rows.addView(row);
    }

    private void addChoice(
        LayoutInflater li,
        LinearLayout rows,
        int titleRes,
        int descRes,
        String[] values,
        int[] labelRes,
        Supplier<String> getter,
        Consumer<String> setter) {
      View row = li.inflate(R.layout.row_choice, rows, false);
      ((TextView) row.findViewById(R.id.row_title)).setText(text(titleRes));
      ((TextView) row.findViewById(R.id.row_desc)).setText(text(descRes));
      TextView valueView = row.findViewById(R.id.row_value);
      valueView.setText(text(labelRes[indexFor(getter.get(), values)]));
      row.setOnClickListener(
          v -> {
            int next = (indexFor(getter.get(), values) + 1) % values.length;
            setter.accept(values[next]);
            valueView.setText(text(labelRes[next]));
          });
      rows.addView(row);
    }

    private static int indexFor(String value, String[] values) {
      for (int i = 0; i < values.length; i++) {
        if (values[i].equals(value)) {
          return i;
        }
      }
      return 0;
    }
  }
}
