package ir.hanzodev1375.csv;

import java.util.ArrayList;
import java.util.List;

/** Small RFC-4180 CSV parser (quotes, escaped quotes, embedded newlines, CRLF, BOM). */
final class CsvParser {

  static final char[] CANDIDATES = {',', ';', '\t', '|'};
  static final int MAX_ROWS = 200_000;

  static final class Result {
    final List<String[]> rows;
    final int maxCols;
    final boolean truncated;

    Result(List<String[]> rows, int maxCols, boolean truncated) {
      this.rows = rows;
      this.maxCols = maxCols;
      this.truncated = truncated;
    }
  }

  private CsvParser() {}

  /** Picks the delimiter whose per-line count is the most consistent over the first lines. */
  static char detectDelimiter(String text) {
    if (text == null || text.isEmpty()) {
      return ',';
    }
    int limit = Math.min(text.length(), 64 * 1024);
    char best = ',';
    long bestScore = -1;
    for (char cand : CANDIDATES) {
      List<Integer> counts = new ArrayList<>();
      int count = 0;
      boolean inQuotes = false;
      for (int i = 0; i < limit && counts.size() < 30; i++) {
        char c = text.charAt(i);
        if (c == '"') {
          inQuotes = !inQuotes;
        } else if (!inQuotes && c == cand) {
          count++;
        } else if (!inQuotes && c == '\n') {
          counts.add(count);
          count = 0;
        }
      }
      if (counts.isEmpty()) {
        counts.add(count);
      }
      int first = counts.get(0);
      if (first == 0) {
        continue;
      }
      int same = 0;
      for (int n : counts) {
        if (n == first) {
          same++;
        }
      }
      long score = (long) same * 1000 + Math.min(first, 200);
      if (score > bestScore) {
        bestScore = score;
        best = cand;
      }
    }
    return best;
  }

  static Result parse(String text, char delim) {
    List<String[]> rows = new ArrayList<>();
    int maxCols = 0;
    boolean truncated = false;
    if (text == null || text.isEmpty()) {
      return new Result(rows, 0, false);
    }
    int i = text.charAt(0) == '\uFEFF' ? 1 : 0;
    int n = text.length();
    StringBuilder sb = new StringBuilder();
    List<String> row = new ArrayList<>();
    boolean inQuotes = false;
    boolean quotedField = false;

    while (i < n) {
      char c = text.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < n && text.charAt(i + 1) == '"') {
            sb.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          sb.append(c);
        }
      } else if (c == '"' && sb.length() == 0 && !quotedField) {
        inQuotes = true;
        quotedField = true;
      } else if (c == delim) {
        row.add(sb.toString());
        sb.setLength(0);
        quotedField = false;
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
          i++;
        }
        boolean blank = row.isEmpty() && sb.length() == 0 && !quotedField;
        if (!blank) {
          row.add(sb.toString());
          maxCols = Math.max(maxCols, row.size());
          rows.add(row.toArray(new String[0]));
        }
        row.clear();
        sb.setLength(0);
        quotedField = false;
        if (rows.size() >= MAX_ROWS) {
          truncated = i + 1 < n;
          return new Result(rows, maxCols, truncated);
        }
      } else {
        sb.append(c);
      }
      i++;
    }
    if (!row.isEmpty() || sb.length() > 0 || quotedField) {
      row.add(sb.toString());
      maxCols = Math.max(maxCols, row.size());
      rows.add(row.toArray(new String[0]));
    }
    return new Result(rows, maxCols, truncated);
  }
}
