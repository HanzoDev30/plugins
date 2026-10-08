package ir.hanzodev30.nodeui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class LibCatalog {

  private LibCatalog() {}

  static final class Entry {
    final String name;
    final int summaryRes;

    Entry(String name, int summaryRes) {
      this.name = name;
      this.summaryRes = summaryRes;
    }
  }

  static final class Group {
    final int titleRes;
    final List<Entry> entries;

    Group(int titleRes, List<Entry> entries) {
      this.titleRes = titleRes;
      this.entries = entries;
    }
  }

  static List<Group> groups() {
    List<Group> groups = new ArrayList<>();

    groups.add(
        new Group(
            R.string.nodeui_group_frontend,
            Arrays.asList(
                new Entry("react", R.string.nodeui_pkg_react_summary),
                new Entry("vue", R.string.nodeui_pkg_vue_summary),
                new Entry("next", R.string.nodeui_pkg_next_summary),
                new Entry("vite", R.string.nodeui_pkg_vite_summary),
                new Entry("tailwindcss", R.string.nodeui_pkg_tailwindcss_summary))));

    groups.add(
        new Group(
            R.string.nodeui_group_backend,
            Arrays.asList(
                new Entry("express", R.string.nodeui_pkg_express_summary),
                new Entry("axios", R.string.nodeui_pkg_axios_summary))));

    groups.add(
        new Group(
            R.string.nodeui_group_tooling,
            Arrays.asList(
                new Entry("typescript", R.string.nodeui_pkg_typescript_summary),
                new Entry("eslint", R.string.nodeui_pkg_eslint_summary),
                new Entry("prettier", R.string.nodeui_pkg_prettier_summary))));

    return groups;
  }
}
