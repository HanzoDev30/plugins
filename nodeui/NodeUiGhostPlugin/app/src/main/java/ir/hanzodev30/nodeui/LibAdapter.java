package ir.hanzodev30.nodeui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LibAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  interface Listener {
    void onInstall(String name);
  }

  private static final int TYPE_SECTION = 0;
  private static final int TYPE_PACKAGE = 1;

  private static final class Row {
    final int type;
    final int titleRes;
    final String text;
    final LibCatalog.Entry entry;

    Row(int type, int titleRes, String text, LibCatalog.Entry entry) {
      this.type = type;
      this.titleRes = titleRes;
      this.text = text;
      this.entry = entry;
    }
  }

  private final Context context;
  private final Listener listener;
  private final List<LibCatalog.Group> groups;
  private final List<Row> rows = new ArrayList<>();
  private String query = "";

  LibAdapter(Context context, Listener listener) {
    this.context = context;
    this.listener = listener;
    this.groups = LibCatalog.groups();
    rebuild();
  }

  void filter(String text) {
    String next = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    if (next.equals(query)) {
      return;
    }
    query = next;
    rebuild();
  }

  private void rebuild() {
    rows.clear();
    for (LibCatalog.Group group : groups) {
      List<LibCatalog.Entry> visible = new ArrayList<>();
      for (LibCatalog.Entry entry : group.entries) {
        if (matches(entry)) {
          visible.add(entry);
        }
      }
      if (visible.isEmpty()) {
        continue;
      }
      rows.add(new Row(TYPE_SECTION, group.titleRes, null, null));
      for (LibCatalog.Entry entry : visible) {
        rows.add(new Row(TYPE_PACKAGE, 0, null, entry));
      }
    }
    if (rows.isEmpty()) {
      rows.add(new Row(TYPE_SECTION, 0, context.getString(R.string.nodeui_no_results), null));
    }
    notifyDataSetChanged();
  }

  private boolean matches(LibCatalog.Entry entry) {
    if (query.isEmpty()) {
      return true;
    }
    if (entry.name.toLowerCase(Locale.ROOT).contains(query)) {
      return true;
    }
    return context.getString(entry.summaryRes).toLowerCase(Locale.ROOT).contains(query);
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  @Override
  public int getItemViewType(int position) {
    return rows.get(position).type;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_SECTION) {
      return new SectionHolder(inflater.inflate(R.layout.nodeui_section, parent, false));
    }
    return new PackageHolder(inflater.inflate(R.layout.nodeui_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Row row = rows.get(position);
    if (holder instanceof SectionHolder) {
      SectionHolder section = (SectionHolder) holder;
      section.title.setText(row.titleRes != 0 ? context.getString(row.titleRes) : row.text);
      section.title.setTextColor(Palette.primary());
      return;
    }

    PackageHolder item = (PackageHolder) holder;
    LibCatalog.Entry entry = row.entry;
    item.name.setText(entry.name);
    item.name.setTextColor(Palette.onSurface());
    item.summary.setText(context.getString(entry.summaryRes));
    item.summary.setTextColor(Palette.onSurfaceVariant());
    item.install.setTextColor(Palette.primary());
    item.install.setOnClickListener(v -> listener.onInstall(entry.name));
  }

  static final class SectionHolder extends RecyclerView.ViewHolder {
    final TextView title;

    SectionHolder(@NonNull View view) {
      super(view);
      title = view.findViewById(R.id.nodeui_section_title);
    }
  }

  static final class PackageHolder extends RecyclerView.ViewHolder {
    final TextView name;
    final TextView summary;
    final Button install;

    PackageHolder(@NonNull View view) {
      super(view);
      name = view.findViewById(R.id.nodeui_item_name);
      summary = view.findViewById(R.id.nodeui_item_summary);
      install = view.findViewById(R.id.nodeui_item_install);
    }
  }
}
