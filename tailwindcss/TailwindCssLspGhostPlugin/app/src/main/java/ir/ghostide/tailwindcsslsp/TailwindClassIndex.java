package ir.ghostide.tailwindcsslsp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tailwind utilities are generated on demand, so there is no single css file that lists every class.
 * The language server derives them from the installed Tailwind theme, so this index does the same:
 * it reads the project's own node_modules/tailwindcss/theme.css (or the theme embedded in the
 * installed @tailwindcss/language-server bundle) and expands the CSS custom properties it finds
 * (--color-*, --text-*, --radius-* ...) into the matching utility class names. A built-in baseline
 * of the default Tailwind palette and core layout utilities keeps the list usable when only the
 * installed language server is present.
 */
final class TailwindClassIndex {

  private static final String[] RELATIVE = {
    "node_modules/tailwindcss/theme.css",
    "node_modules/tailwindcss/index.css",
  };

  private static final int MAX_PARENT_LEVELS = 8;
  private static final long MAX_BYTES = 8L * 1024 * 1024;

  private static final Pattern VAR = Pattern.compile("--([a-zA-Z][a-zA-Z0-9-]*)\\s*:");

  private static final String[] COLOR_PREFIXES = {
    "bg", "text", "border", "divide", "outline", "ring", "ring-offset", "fill", "stroke",
    "from", "via", "to", "decoration", "accent", "caret", "placeholder", "shadow", "inset-shadow",
  };

  private static final String[] HUES = {
    "slate", "gray", "zinc", "neutral", "stone", "red", "orange", "amber", "yellow", "lime",
    "green", "emerald", "teal", "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia",
    "pink", "rose",
  };

  private static final String[] SHADES = {
    "50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "950",
  };

  private static final String[] SPACING_PREFIXES = {
    "p", "px", "py", "ps", "pe", "pt", "pr", "pb", "pl",
    "m", "mx", "my", "ms", "me", "mt", "mr", "mb", "ml",
    "gap", "gap-x", "gap-y", "space-x", "space-y",
    "inset", "inset-x", "inset-y", "start", "end", "top", "right", "bottom", "left",
    "w", "min-w", "max-w", "h", "min-h", "max-h", "size", "basis",
  };

  private static final String[] NEGATABLE = {
    "m", "mx", "my", "ms", "me", "mt", "mr", "mb", "ml",
    "inset", "inset-x", "inset-y", "start", "end", "top", "right", "bottom", "left",
  };

  private static final String[] CORE = {
    // display / visibility
    "block", "inline-block", "inline", "flex", "inline-flex", "table", "inline-table",
    "table-caption", "table-cell", "table-column", "table-column-group", "table-footer-group",
    "table-header-group", "table-row-group", "table-row", "flow-root", "grid", "inline-grid",
    "contents", "list-item", "hidden", "visible", "invisible", "collapse",
    // position
    "static", "fixed", "absolute", "relative", "sticky",
    // z-index / ordering
    "z-auto", "order-first", "order-last", "order-none",
    // overflow
    "overflow-auto", "overflow-hidden", "overflow-clip", "overflow-visible", "overflow-scroll",
    "overflow-x-auto", "overflow-x-hidden", "overflow-x-clip", "overflow-x-visible", "overflow-x-scroll",
    "overflow-y-auto", "overflow-y-hidden", "overflow-y-clip", "overflow-y-visible", "overflow-y-scroll",
    // flexbox
    "flex-row", "flex-row-reverse", "flex-col", "flex-col-reverse", "flex-wrap", "flex-wrap-reverse",
    "flex-nowrap", "flex-1", "flex-auto", "flex-initial", "flex-none", "grow", "grow-0",
    "shrink", "shrink-0", "basis-auto", "basis-full",
    "justify-normal", "justify-start", "justify-end", "justify-center", "justify-between",
    "justify-around", "justify-evenly", "justify-stretch",
    "justify-items-start", "justify-items-end", "justify-items-center", "justify-items-stretch",
    "justify-self-auto", "justify-self-start", "justify-self-end", "justify-self-center",
    "justify-self-stretch",
    "items-start", "items-end", "items-center", "items-baseline", "items-stretch",
    "content-normal", "content-center", "content-start", "content-end", "content-between",
    "content-around", "content-evenly", "content-baseline", "content-stretch",
    "self-auto", "self-start", "self-end", "self-center", "self-stretch", "self-baseline",
    "place-content-center", "place-content-start", "place-content-end", "place-content-between",
    "place-content-around", "place-content-evenly", "place-content-stretch",
    "place-items-start", "place-items-end", "place-items-center", "place-items-stretch",
    "place-self-auto", "place-self-start", "place-self-end", "place-self-center", "place-self-stretch",
    // grid
    "grid-flow-row", "grid-flow-col", "grid-flow-dense", "grid-flow-row-dense", "grid-flow-col-dense",
    "col-auto", "col-span-full", "row-auto", "row-span-full", "grid-cols-none", "grid-rows-none",
    "auto-cols-auto", "auto-cols-min", "auto-cols-max", "auto-cols-fr",
    "auto-rows-auto", "auto-rows-min", "auto-rows-max", "auto-rows-fr",
    // sizing
    "w-auto", "w-full", "w-screen", "w-svw", "w-lvw", "w-dvw", "w-min", "w-max", "w-fit",
    "min-w-0", "min-w-full", "min-w-min", "min-w-max", "min-w-fit",
    "max-w-none", "max-w-full", "max-w-min", "max-w-max", "max-w-fit", "max-w-screen-sm",
    "max-w-screen-md", "max-w-screen-lg", "max-w-screen-xl", "max-w-screen-2xl", "max-w-prose",
    "h-auto", "h-full", "h-screen", "h-svh", "h-lvh", "h-dvh", "h-min", "h-max", "h-fit",
    "min-h-0", "min-h-full", "min-h-screen", "min-h-svh", "min-h-lvh", "min-h-dvh", "min-h-min",
    "min-h-max", "min-h-fit",
    "max-h-none", "max-h-full", "max-h-screen", "max-h-svh", "max-h-lvh", "max-h-dvh", "max-h-min",
    "max-h-max", "max-h-fit",
    "size-auto", "size-full", "size-min", "size-max", "size-fit", "aspect-auto", "aspect-square",
    "aspect-video",
    // spacing keywords
    "inset-auto", "inset-full", "inset-x-auto", "inset-x-full", "inset-y-auto", "inset-y-full",
    "start-auto", "end-auto", "top-auto", "top-full", "right-auto", "right-full",
    "bottom-auto", "bottom-full", "left-auto", "left-full",
    // typography
    "font-thin", "font-extralight", "font-light", "font-normal", "font-medium", "font-semibold",
    "font-bold", "font-extrabold", "font-black", "font-sans", "font-serif", "font-mono",
    "text-left", "text-center", "text-right", "text-justify", "text-start", "text-end",
    "text-xs", "text-sm", "text-base", "text-lg", "text-xl", "text-2xl", "text-3xl", "text-4xl",
    "text-5xl", "text-6xl", "text-7xl", "text-8xl", "text-9xl",
    "italic", "not-italic", "uppercase", "lowercase", "capitalize", "normal-case",
    "underline", "overline", "line-through", "no-underline",
    "decoration-solid", "decoration-double", "decoration-dotted", "decoration-dashed",
    "decoration-wavy", "decoration-auto", "decoration-from-font", "decoration-0", "decoration-1",
    "decoration-2", "decoration-4", "decoration-8", "underline-offset-auto", "underline-offset-0",
    "underline-offset-1", "underline-offset-2", "underline-offset-4", "underline-offset-8",
    "truncate", "text-ellipsis", "text-clip", "text-wrap", "text-nowrap", "text-balance",
    "text-pretty", "indent-0", "align-baseline", "align-top", "align-middle", "align-bottom",
    "align-text-top", "align-text-bottom", "whitespace-normal", "whitespace-nowrap",
    "whitespace-pre", "whitespace-pre-line", "whitespace-pre-wrap", "whitespace-break-spaces",
    "break-normal", "break-words", "break-all", "break-keep",
    "tracking-tighter", "tracking-tight", "tracking-normal", "tracking-wide", "tracking-wider",
    "tracking-widest", "leading-none", "leading-tight", "leading-snug", "leading-normal",
    "leading-relaxed", "leading-loose",
    "list-none", "list-disc", "list-decimal", "list-inside", "list-outside",
    "placeholder-transparent", "placeholder-current",
    // borders
    "border", "border-0", "border-2", "border-4", "border-8",
    "border-x", "border-x-0", "border-x-2", "border-x-4", "border-x-8",
    "border-y", "border-y-0", "border-y-2", "border-y-4", "border-y-8",
    "border-t", "border-t-0", "border-t-2", "border-t-4", "border-t-8",
    "border-r", "border-r-0", "border-r-2", "border-r-4", "border-r-8",
    "border-b", "border-b-0", "border-b-2", "border-b-4", "border-b-8",
    "border-l", "border-l-0", "border-l-2", "border-l-4", "border-l-8",
    "border-s", "border-s-0", "border-s-2", "border-s-4", "border-s-8",
    "border-e", "border-e-0", "border-e-2", "border-e-4", "border-e-8",
    "border-solid", "border-dashed", "border-dotted", "border-double", "border-hidden", "border-none",
    "rounded-none", "rounded-sm", "rounded", "rounded-md", "rounded-lg", "rounded-xl", "rounded-2xl",
    "rounded-3xl", "rounded-4xl", "rounded-full",
    "rounded-t", "rounded-t-none", "rounded-t-sm", "rounded-t-md", "rounded-t-lg", "rounded-t-xl",
    "rounded-t-2xl", "rounded-t-3xl", "rounded-t-full",
    "rounded-r", "rounded-r-none", "rounded-r-sm", "rounded-r-md", "rounded-r-lg", "rounded-r-xl",
    "rounded-r-full", "rounded-b", "rounded-b-none", "rounded-b-sm", "rounded-b-md", "rounded-b-lg",
    "rounded-b-xl", "rounded-b-full", "rounded-l", "rounded-l-none", "rounded-l-sm", "rounded-l-md",
    "rounded-l-lg", "rounded-l-xl", "rounded-l-full",
    // ring / outline
    "ring", "ring-0", "ring-1", "ring-2", "ring-4", "ring-8", "ring-inset",
    "ring-offset-0", "ring-offset-1", "ring-offset-2", "ring-offset-4", "ring-offset-8",
    "outline-none", "outline", "outline-0", "outline-1", "outline-2", "outline-4", "outline-8",
    "outline-hidden", "outline-offset-0", "outline-offset-1", "outline-offset-2",
    "outline-offset-4", "outline-offset-8", "outline-solid", "outline-dashed", "outline-dotted",
    "outline-double",
    // effects
    "shadow", "shadow-2xs", "shadow-xs", "shadow-sm", "shadow-md", "shadow-lg", "shadow-xl",
    "shadow-2xl", "shadow-none", "shadow-inner",
    "inset-shadow-2xs", "inset-shadow-xs", "inset-shadow-sm", "inset-shadow-none",
    "drop-shadow", "drop-shadow-xs", "drop-shadow-sm", "drop-shadow-md", "drop-shadow-lg",
    "drop-shadow-xl", "drop-shadow-2xl", "drop-shadow-none",
    "blur-none", "blur", "blur-xs", "blur-sm", "blur-md", "blur-lg", "blur-xl", "blur-2xl", "blur-3xl",
    "brightness-0", "brightness-50", "brightness-75", "brightness-90", "brightness-95",
    "brightness-100", "brightness-105", "brightness-110", "brightness-125", "brightness-150",
    "brightness-200", "contrast-0", "contrast-50", "contrast-75", "contrast-100", "contrast-125",
    "contrast-150", "contrast-200", "grayscale", "grayscale-0", "invert", "invert-0",
    "sepia", "sepia-0", "saturate-0", "saturate-50", "saturate-100", "saturate-150", "saturate-200",
    "backdrop-blur-none", "backdrop-blur", "backdrop-blur-sm", "backdrop-blur-md", "backdrop-blur-lg",
    "mix-blend-normal", "mix-blend-multiply", "mix-blend-screen", "mix-blend-overlay",
    "bg-blend-normal", "bg-blend-multiply", "bg-blend-screen",
    // tables
    "border-collapse", "border-separate", "table-auto", "table-fixed", "caption-top", "caption-bottom",
    // transitions / transforms
    "transition", "transition-none", "transition-all", "transition-colors", "transition-opacity",
    "transition-shadow", "transition-transform", "duration-0", "duration-75", "duration-100",
    "duration-150", "duration-200", "duration-300", "duration-500", "duration-700", "duration-1000",
    "ease-linear", "ease-in", "ease-out", "ease-in-out", "delay-0", "delay-75", "delay-100",
    "delay-150", "delay-200", "delay-300", "delay-500", "delay-700", "delay-1000",
    "animate-none", "animate-spin", "animate-ping", "animate-pulse", "animate-bounce",
    "transform", "transform-gpu", "transform-none",
    "scale-0", "scale-50", "scale-75", "scale-90", "scale-95", "scale-100", "scale-105", "scale-110",
    "scale-125", "scale-150", "scale-x-0", "scale-x-50", "scale-x-100", "scale-y-0", "scale-y-50",
    "scale-y-100", "rotate-0", "rotate-1", "rotate-2", "rotate-3", "rotate-6", "rotate-12",
    "rotate-45", "rotate-90", "rotate-180", "translate-x-0", "translate-y-0",
    "origin-center", "origin-top", "origin-top-right", "origin-right", "origin-bottom-right",
    "origin-bottom", "origin-bottom-left", "origin-left", "origin-top-left",
    "skew-x-0", "skew-y-0",
    // interactivity
    "cursor-auto", "cursor-default", "cursor-pointer", "cursor-wait", "cursor-text", "cursor-move",
    "cursor-help", "cursor-not-allowed", "cursor-none", "cursor-progress", "cursor-grab",
    "cursor-grabbing", "cursor-crosshair", "cursor-zoom-in", "cursor-zoom-out",
    "pointer-events-none", "pointer-events-auto", "resize", "resize-none", "resize-x", "resize-y",
    "select-none", "select-text", "select-all", "select-auto", "scroll-auto", "scroll-smooth",
    "snap-none", "snap-x", "snap-y", "snap-both", "snap-mandatory", "snap-proximity",
    "snap-start", "snap-end", "snap-center", "snap-align-none", "appearance-none", "appearance-auto",
    "touch-auto", "touch-none", "touch-pan-x", "touch-pan-y", "touch-manipulation",
    "will-change-auto", "will-change-scroll", "will-change-contents", "will-change-transform",
    // accessibility
    "sr-only", "not-sr-only", "forced-color-adjust-auto", "forced-color-adjust-none",
    // backgrounds
    "bg-fixed", "bg-local", "bg-scroll", "bg-clip-border", "bg-clip-padding", "bg-clip-content",
    "bg-clip-text", "bg-origin-border", "bg-origin-padding", "bg-origin-content",
    "bg-auto", "bg-cover", "bg-contain", "bg-none", "bg-center", "bg-top", "bg-right-top",
    "bg-right", "bg-right-bottom", "bg-bottom", "bg-left-bottom", "bg-left", "bg-left-top",
    "bg-repeat", "bg-no-repeat", "bg-repeat-x", "bg-repeat-y", "bg-repeat-round", "bg-repeat-space",
    "object-contain", "object-cover", "object-fill", "object-none", "object-scale-down",
    "object-center", "object-top", "object-bottom", "object-left", "object-right",
    "m-auto", "mx-auto", "my-auto", "ms-auto", "me-auto", "mt-auto", "mr-auto", "mb-auto",
    "ml-auto", "w-px", "h-px", "max-w-xs", "max-w-sm", "max-w-md", "max-w-lg", "max-w-xl",
    "max-w-2xl", "max-w-3xl", "max-w-4xl", "max-w-5xl", "max-w-6xl", "max-w-7xl", "max-w-prose",
    "bg-gradient-to-t", "bg-gradient-to-tr", "bg-gradient-to-r", "bg-gradient-to-br",
    "bg-gradient-to-b", "bg-gradient-to-bl", "bg-gradient-to-l", "bg-gradient-to-tl",
    "bg-linear-to-t", "bg-linear-to-tr", "bg-linear-to-r", "bg-linear-to-br", "bg-linear-to-b",
    "bg-linear-to-bl", "bg-linear-to-l", "bg-linear-to-tl", "bg-radial", "bg-conic",
    // arbitrary-value friendly helpers
    "fill-none", "fill-current", "stroke-none", "stroke-current",
  };

  private final Set<String> names = new HashSet<>();
  private File source;
  private long stamp = Long.MIN_VALUE;

  Set<String> names() {
    return names;
  }

  File source() {
    return source;
  }

  void refresh(File baseDir, File projectRoot, File fallback) {
    File theme = find(baseDir, projectRoot);
    if (theme == null && fallback != null && fallback.isFile()) theme = fallback;

    long mtime = theme == null ? Long.MIN_VALUE : theme.lastModified();
    boolean unchanged = theme == null ? source == null : (theme.equals(source) && mtime == stamp);
    if (unchanged && !names.isEmpty()) return;

    Set<String> built = baseline();
    if (theme != null) built.addAll(fromTheme(read(theme)));

    names.clear();
    names.addAll(built);
    source = theme;
    stamp = mtime;
  }

  private static File find(File baseDir, File projectRoot) {
    File found = search(baseDir);
    if (found != null) return found;
    return search(projectRoot);
  }

  private static File search(File start) {
    File dir = start;
    for (int i = 0; dir != null && i < MAX_PARENT_LEVELS; i++) {
      for (String rel : RELATIVE) {
        File f = new File(dir, rel);
        if (f.isFile()) return f;
      }
      dir = dir.getParentFile();
    }
    return null;
  }

  private static Set<String> fromTheme(String css) {
    Set<String> out = new HashSet<>();
    if (css.isEmpty()) return out;

    Matcher m = VAR.matcher(css);
    while (m.find()) {
      String name = m.group(1);
      int sep = name.indexOf("--");
      if (sep >= 0) name = name.substring(0, sep);

      if (name.startsWith("color-")) {
        addColors(out, name.substring(6));
      } else if (name.startsWith("inset-shadow-")) {
        addNamed(out, "inset-shadow", name.substring(13));
      } else if (name.startsWith("drop-shadow-")) {
        addNamed(out, "drop-shadow", name.substring(12));
      } else if (name.startsWith("text-shadow-")) {
        addNamed(out, "text-shadow", name.substring(12));
      } else if (name.startsWith("shadow-")) {
        addNamed(out, "shadow", name.substring(7));
      } else if (name.startsWith("font-weight-")) {
        addNamed(out, "font", name.substring(12));
      } else if (name.startsWith("font-")) {
        addNamed(out, "font", name.substring(5));
      } else if (name.startsWith("text-")) {
        addNamed(out, "text", name.substring(5));
      } else if (name.startsWith("tracking-")) {
        addNamed(out, "tracking", name.substring(9));
      } else if (name.startsWith("leading-")) {
        addNamed(out, "leading", name.substring(8));
      } else if (name.startsWith("radius-")) {
        addNamed(out, "rounded", name.substring(7));
      } else if (name.startsWith("blur-")) {
        addNamed(out, "blur", name.substring(5));
      } else if (name.startsWith("opacity-")) {
        addNamed(out, "opacity", name.substring(8));
      } else if (name.startsWith("aspect-")) {
        addNamed(out, "aspect", name.substring(7));
      } else if (name.startsWith("animate-")) {
        addNamed(out, "animate", name.substring(8));
      } else if (name.startsWith("ease-")) {
        addNamed(out, "ease", name.substring(5));
      } else if (name.startsWith("perspective-")) {
        addNamed(out, "perspective", name.substring(12));
      } else if (name.startsWith("container-")) {
        addNamed(out, "max-w", name.substring(10));
      } else if (name.startsWith("max-width-")) {
        addNamed(out, "max-w", name.substring(10));
      }
    }
    return out;
  }

  private static void addNamed(Set<String> out, String prefix, String suffix) {
    if (suffix == null || suffix.isEmpty()) {
      out.add(prefix);
    } else {
      out.add(prefix + "-" + suffix);
    }
  }

  private static void addColors(Set<String> out, String color) {
    if (color.isEmpty()) return;
    for (String prefix : COLOR_PREFIXES) {
      out.add(prefix + "-" + color);
    }
  }

  private static Set<String> baseline() {
    Set<String> out = new HashSet<>();
    for (String c : CORE) out.add(c);

    for (String hue : HUES) {
      for (String shade : SHADES) {
        addColors(out, hue + "-" + shade);
      }
    }
    for (String plain : new String[] {"black", "white", "transparent", "current", "inherit"}) {
      addColors(out, plain);
    }

    String[] steps = {
      "0", "0.5", "1", "1.5", "2", "2.5", "3", "3.5", "4", "5", "6", "7", "8", "9", "10", "11",
      "12", "14", "16", "20", "24", "28", "32", "36", "40", "44", "48", "52", "56", "60", "64",
      "72", "80", "96",
    };
    for (String prefix : SPACING_PREFIXES) {
      for (String step : steps) out.add(prefix + "-" + step);
    }
    for (String prefix : NEGATABLE) {
      for (String step : steps) out.add("-" + prefix + "-" + step);
    }

    for (int i = 0; i <= 100; i += 5) out.add("opacity-" + i);
    for (int i = 0; i <= 50; i += 10) out.add("z-" + i);
    for (int i = 1; i <= 12; i++) {
      out.add("order-" + i);
      out.add("grid-cols-" + i);
      out.add("col-span-" + i);
    }
    for (int i = 1; i <= 6; i++) {
      out.add("grid-rows-" + i);
      out.add("row-span-" + i);
    }
    for (int i = 1; i <= 12; i++) out.add("columns-" + i);
    for (String b : new String[] {"sm", "md", "lg", "xl", "2xl"}) out.add("max-w-screen-" + b);

    return out;
  }

  private static String read(File file) {
    try (InputStream in = new FileInputStream(file)) {
      byte[] bytes = in.readAllBytes();
      if (bytes.length > MAX_BYTES) return "";
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }
}
