package ir.ghostide.pipui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The curated list shown when the panel opens, so the common cases need no typing at all.
 *
 * <p>Every entry is a plain pip requirement; the install button prepends the guard copied from the
 * host's own {@code CodeRuner} so a missing interpreter is installed first instead of failing.
 */
final class PackageCatalog {

  private PackageCatalog() {}

  static final class Entry {
    final String name;
    final String summary;
    final String requirement;

    Entry(String name, String summary, String requirement) {
      this.name = name;
      this.summary = summary;
      this.requirement = requirement;
    }
  }

  static final class Group {
    final String title;
    final List<Entry> entries;

    Group(String title, List<Entry> entries) {
      this.title = title;
      this.entries = entries;
    }
  }

  static List<Group> groups() {
    List<Group> groups = new ArrayList<>();

    groups.add(
        new Group(
            "Essentials",
            Arrays.asList(
                new Entry("requests", "HTTP library, the usual starting point", "requests"),
                new Entry("pip", "Keeps pip itself current", "--upgrade pip"),
                new Entry("setuptools", "Package build backend used by setup.py", "setuptools wheel"),
                new Entry("build", "PEP 517 build frontend", "build"),
                new Entry("wheel", "Prebuilt wheels for fast installs", "wheel"),
                new Entry("virtualenv", "Isolated environments without venv", "virtualenv"),
                new Entry("rich", "Pretty tables, progress bars and tracebacks", "rich"),
                new Entry("colorama", "Coloured output on Windows terminals", "colorama"))));

    groups.add(
        new Group(
            "Web & API",
            Arrays.asList(
                new Entry("flask", "Lightweight WSGI web app", "flask"),
                new Entry("fastapi", "Async web framework with OpenAPI docs", "fastapi"),
                new Entry("uvicorn", "ASGI server for FastAPI", "uvicorn"),
                new Entry("django", "Full web framework with admin and ORM", "django"),
                new Entry("httpx", "HTTP client with async and HTTP/2", "httpx"),
                new Entry("pydantic", "Data validation from type hints", "pydantic"),
                new Entry("python-dotenv", "Loads .env files", "python-dotenv"),
                new Entry("jinja2", "Templating engine", "jinja2"))));

    groups.add(
        new Group(
            "Data & Science",
            Arrays.asList(
                new Entry("numpy", "N-dimensional arrays and math core", "numpy"),
                new Entry("pandas", "DataFrames and CSV/Excel handling", "pandas"),
                new Entry("matplotlib", "Plotting", "matplotlib"),
                new Entry("scipy", "Scientific computing routines", "scipy"),
                new Entry("scikit-learn", "Machine learning", "scikit-learn"),
                new Entry("pillow", "Image open/save/resize", "pillow"),
                new Entry("openpyxl", "Reads and writes .xlsx", "openpyxl"),
                new Entry("lxml", "Fast XML and HTML parsing", "lxml"),
                new Entry("beautifulsoup4", "HTML scraping", "beautifulsoup4"))));

    groups.add(
        new Group(
            "Dev & Tooling",
            Arrays.asList(
                new Entry("pytest", "Test runner", "pytest"),
                new Entry("black", "Opinionated code formatter", "black"),
                new Entry("ruff", "Fast linter and formatter", "ruff"),
                new Entry("mypy", "Static type checker", "mypy"),
                new Entry("pylint", "Deep linting", "pylint"),
                new Entry("ipython", "Interactive REPL", "ipython"),
                new Entry("debugpy", "Debugger adapter for VS Code", "debugpy"),
                new Entry("pyinstaller", "Freezes scripts into a binary", "pyinstaller"))));

    groups.add(
        new Group(
            "Automation",
            Arrays.asList(
                new Entry("click", "Command line interfaces", "click"),
                new Entry("typer", "CLI built on Click and type hints", "typer"),
                new Entry("schedule", "Cron-like job scheduling in Python", "schedule"),
                new Entry("paramiko", "SSH client", "paramiko"),
                new Entry("psutil", "Process and system metrics", "psutil"),
                new Entry("watchdog", "Filesystem change events", "watchdog"),
                new Entry("openai", "OpenAI API client", "openai"),
                new Entry("python-telegram-bot", "Telegram bots", "python-telegram-bot"))));

    return groups;
  }
}
