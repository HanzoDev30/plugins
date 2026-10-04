#!/usr/bin/env python3
"""Generates the rainbow-CSV TextMate grammars (one per delimiter) into app/src/main/assets/grammars."""
import json, os

COLS = 12
CYCLE = [  # standard scope prefixes so every existing editor theme colours them
    "entity.name.function", "string.unquoted", "keyword.control", "constant.character",
    "support.type", "variable.parameter", "entity.name.type", "constant.other",
    "storage.type", "support.function", "entity.name.tag", "variable.other",
]
VARIANTS = [  # (file, scope, display, delimiter, class-form, literal-form)
    ("csv",           "source.csv",           "CSV",             ",",  ",",   ","),
    ("csv-semicolon", "source.csv.semicolon", "CSV (semicolon)", ";",  ";",   ";"),
    ("tsv",           "source.tsv",           "TSV",             "\t", "\\t", "\\t"),
    ("csv-pipe",      "source.csv.pipe",      "CSV (pipe)",      "|",  "|",   "\\|"),
]

def build(file, scope, name, delim, dcls, dlit):
    field = '(?:"(?:[^"]|"")*"|[^%s"\\r\\n]*)' % dcls
    # nested optionals: F1 (D F2 (D F3 (... (tail)?)?)?)?
    tail = "([^\\r\\n]*)"
    rx = ""
    for i in range(COLS, 0, -1):
        if i == COLS:
            inner = "(%s)" % field + tail
        else:
            inner = "(%s)" % field + "(?:(%s)" % dlit + rx + ")?"
        rx = inner
    # group numbering: F1=1, D1=2, F2=3, D2=4 ... F12=23, tail=24
    # rebuild with explicit numbering so the capture list is obvious
    parts = []
    close = 0
    for i in range(1, COLS + 1):
        parts.append("(%s)" % field)
        if i < COLS:
            parts.append("(?:(%s)" % dlit)
            close += 1
    parts.append(tail)
    rx = "".join(parts) + ")?" * close

    def caps(header):
        c = {}
        for i in range(1, COLS + 1):
            c[str(2 * i - 1)] = {"name": "markup.heading.csv" if header else "%s.csv%d" % (CYCLE[i - 1], i)}
            if i < COLS:
                c[str(2 * i)] = {"name": "punctuation.separator.csv"}
        c[str(2 * COLS)] = {"name": "meta.tail.csv", "patterns": [{"include": "#fallback"}]}
        return c

    return {
        "name": name,
        "scopeName": scope,
        "fileTypes": [file.split("-")[0]],
        "patterns": [{"include": "#header"}, {"include": "#row"}, {"include": "#fallback"}],
        "repository": {
            "header": {"match": "\\A" + rx, "captures": caps(True)},
            "row": {"match": "^" + rx, "captures": caps(False)},
            "fallback": {"patterns": [
                {"name": "string.quoted.double.csv",
                 "begin": "\"", "end": "\"(?!\")",
                 "beginCaptures": {"0": {"name": "punctuation.definition.string.begin.csv"}},
                 "endCaptures": {"0": {"name": "punctuation.definition.string.end.csv"}},
                 "patterns": [{"match": "\"\"", "name": "constant.character.escape.csv"}]},
                {"match": dlit, "name": "punctuation.separator.csv"},
            ]},
        },
    }

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app/src/main/assets/grammars")
os.makedirs(out, exist_ok=True)
for v in VARIANTS:
    with open(os.path.join(out, v[0] + ".tmLanguage.json"), "w", encoding="utf-8") as f:
        json.dump(build(*v), f, indent=2, ensure_ascii=False)
    print("wrote", v[0])
