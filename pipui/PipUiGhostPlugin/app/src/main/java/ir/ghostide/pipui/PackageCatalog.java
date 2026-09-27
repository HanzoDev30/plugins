package ir.ghostide.pipui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The curated list shown when the panel opens, so the common cases need no typing at all.
 *
 * <p>Every entry is a plain pip requirement; the install button prepends the guard copied from the
 * host's own {@code CodeRuner} so a missing interpreter is installed first instead of failing.
 *
 * <p>Group titles and summaries are kept as string resource ids, not literal text, so the panel
 * renders them in the device language; only the package names and the requirement itself are
 * language neutral and stay in code.
 */
final class PackageCatalog {

  private PackageCatalog() {}

  static final class Entry {
    final String name;
    final int summaryRes;
    final String requirement;

    Entry(String name, int summaryRes, String requirement) {
      this.name = name;
      this.summaryRes = summaryRes;
      this.requirement = requirement;
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
            R.string.pipui_group_essentials,
            Arrays.asList(
                new Entry("requests", R.string.pipui_pkg_requests_summary, "requests"),
                new Entry("pip", R.string.pipui_pkg_pip_summary, "--upgrade pip"),
                new Entry(
                    "setuptools",
                    R.string.pipui_pkg_setuptools_summary,
                    "setuptools wheel"),
                new Entry("build", R.string.pipui_pkg_build_summary, "build"),
                new Entry("wheel", R.string.pipui_pkg_wheel_summary, "wheel"),
                new Entry(
                    "virtualenv",
                    R.string.pipui_pkg_virtualenv_summary,
                    "virtualenv"),
                new Entry("rich", R.string.pipui_pkg_rich_summary, "rich"),
                new Entry("colorama", R.string.pipui_pkg_colorama_summary, "colorama"))));

    groups.add(
        new Group(
            R.string.pipui_group_web_api,
            Arrays.asList(
                new Entry("flask", R.string.pipui_pkg_flask_summary, "flask"),
                new Entry("fastapi", R.string.pipui_pkg_fastapi_summary, "fastapi"),
                new Entry("uvicorn", R.string.pipui_pkg_uvicorn_summary, "uvicorn"),
                new Entry("django", R.string.pipui_pkg_django_summary, "django"),
                new Entry("httpx", R.string.pipui_pkg_httpx_summary, "httpx"),
                new Entry("pydantic", R.string.pipui_pkg_pydantic_summary, "pydantic"),
                new Entry(
                    "python-dotenv",
                    R.string.pipui_pkg_python_dotenv_summary,
                    "python-dotenv"),
                new Entry("jinja2", R.string.pipui_pkg_jinja2_summary, "jinja2"))));

    groups.add(
        new Group(
            R.string.pipui_group_data_science,
            Arrays.asList(
                new Entry("numpy", R.string.pipui_pkg_numpy_summary, "numpy"),
                new Entry("pandas", R.string.pipui_pkg_pandas_summary, "pandas"),
                new Entry("matplotlib", R.string.pipui_pkg_matplotlib_summary, "matplotlib"),
                new Entry("scipy", R.string.pipui_pkg_scipy_summary, "scipy"),
                new Entry(
                    "scikit-learn",
                    R.string.pipui_pkg_scikit_learn_summary,
                    "scikit-learn"),
                new Entry("pillow", R.string.pipui_pkg_pillow_summary, "pillow"),
                new Entry("openpyxl", R.string.pipui_pkg_openpyxl_summary, "openpyxl"),
                new Entry("lxml", R.string.pipui_pkg_lxml_summary, "lxml"),
                new Entry(
                    "beautifulsoup4",
                    R.string.pipui_pkg_beautifulsoup4_summary,
                    "beautifulsoup4"))));

    groups.add(
        new Group(
            R.string.pipui_group_dev_tooling,
            Arrays.asList(
                new Entry("pytest", R.string.pipui_pkg_pytest_summary, "pytest"),
                new Entry("black", R.string.pipui_pkg_black_summary, "black"),
                new Entry("ruff", R.string.pipui_pkg_ruff_summary, "ruff"),
                new Entry("mypy", R.string.pipui_pkg_mypy_summary, "mypy"),
                new Entry("pylint", R.string.pipui_pkg_pylint_summary, "pylint"),
                new Entry("ipython", R.string.pipui_pkg_ipython_summary, "ipython"),
                new Entry("debugpy", R.string.pipui_pkg_debugpy_summary, "debugpy"),
                new Entry(
                    "pyinstaller",
                    R.string.pipui_pkg_pyinstaller_summary,
                    "pyinstaller"))));

    groups.add(
        new Group(
            R.string.pipui_group_automation,
            Arrays.asList(
                new Entry("click", R.string.pipui_pkg_click_summary, "click"),
                new Entry("typer", R.string.pipui_pkg_typer_summary, "typer"),
                new Entry("schedule", R.string.pipui_pkg_schedule_summary, "schedule"),
                new Entry("paramiko", R.string.pipui_pkg_paramiko_summary, "paramiko"),
                new Entry("psutil", R.string.pipui_pkg_psutil_summary, "psutil"),
                new Entry("watchdog", R.string.pipui_pkg_watchdog_summary, "watchdog"),
                new Entry("openai", R.string.pipui_pkg_openai_summary, "openai"),
                new Entry(
                    "python-telegram-bot",
                    R.string.pipui_pkg_python_telegram_bot_summary,
                    "python-telegram-bot"))));

    return groups;
  }
}
