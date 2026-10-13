package ir.hanzodev1375.jadx.bridge;

import java.util.Objects;

/** Plain options bean passed from the plugin to the engine. Only JDK types, safe to share. */
public final class JadxOptions {

  /** Rename obfuscated (a, b, c...) classes/methods/fields to readable names. */
  public boolean deobfuscation = false;

  /** Keep code that jadx could not restore cleanly instead of replacing it with a warning. */
  public boolean showBadCode = false;

  /** Raw, bytecode-like output. Very fast and never fails; useful for huge/obfuscated dex files. */
  public boolean fallbackMode = false;

  /** Worker threads for "decompile all". 0 means automatic. */
  public int threads = 0;

  public JadxOptions() {}

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JadxOptions)) {
      return false;
    }
    JadxOptions other = (JadxOptions) o;
    return deobfuscation == other.deobfuscation
        && showBadCode == other.showBadCode
        && fallbackMode == other.fallbackMode
        && threads == other.threads;
  }

  @Override
  public int hashCode() {
    return Objects.hash(deobfuscation, showBadCode, fallbackMode, threads);
  }
}
