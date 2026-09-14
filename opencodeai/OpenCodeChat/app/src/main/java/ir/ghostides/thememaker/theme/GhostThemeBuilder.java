package ir.ghostides.thememaker.theme;

import org.json.JSONObject;

import ir.ghostides.thememaker.palette.Colors;
import ir.ghostides.thememaker.palette.Palette;

/**
 * Builds a GhostIDE theme document from a {@link Palette}.
 *
 * <p>The output matches the structure the IDE's {@code ThemeManager} understands (activity /
 * editor / widget) and is written to be self-contained: every colour key of the default theme is
 * present, so it works both as a standalone file and merged against the host defaults. The color
 * roles follow the mapping used in {@code ThemeUtils#applyEditor} — backgrounds from the palette's
 * background/surface, text and syntax colors from the palette's text / syntax colors.
 */
public final class GhostThemeBuilder {

  private GhostThemeBuilder() {}

  public static JSONObject build(Palette p, String name, String imagePath) {
    int bg = p.background;
    int surface = p.surface;
    int stroke = p.stroke;
    int text = p.text;
    int muted = p.muted;
    int accent = p.accent;
    int[] s = p.syntax;

    int keyword = s[0 % s.length];
    int operator = s[1 % s.length];
    int literal = s[2 % s.length];
    int identifierVar = s[3 % s.length];
    int identifierName = s[4 % s.length];
    int annotation = s[5 % s.length];

    int error = Colors.hueShift(identifierVar, 8f);
    int warn = Colors.hueShift(annotation, -18f);
    int typo = Colors.hueShift(operator, 130f);

    int fabIcon = Colors.luminance(accent) < 0.55f ? Colors.argb(255, 255, 255, 255) : Colors.argb(255, 18, 20, 26);

    JSONObject editor = new JSONObject();
    try {
      editor.put("lineDivider", Colors.hex(stroke));
      editor.put("lineNumber", Colors.hex(muted));
      editor.put("lineNumberBackground", Colors.hex(bg));
      editor.put("wholeBackground", Colors.hex(bg));
      editor.put("textNormal", Colors.hex(text));
      editor.put("selectedTextBackground", Colors.hex(stroke));
      editor.put("selectionInsert", Colors.hex(accent));
      editor.put("selectionHandle", Colors.hex(accent));
      editor.put("currentLine", Colors.hex(surface));
      editor.put("underline", Colors.hex(text));
      editor.put("scrollBarThumb", Colors.hex(stroke));
      editor.put("scrollBarThumbPressed", Colors.hex(accent));
      editor.put("scrollBarTrack", Colors.hex(Colors.mix(bg, Colors.argb(255, 0, 0, 0), 0.25f)));
      editor.put("blockLine", Colors.hex(stroke));
      editor.put("blockLineCurrent", Colors.hex(accent));
      editor.put("lineNumberPanel", Colors.hex(Colors.mix(bg, Colors.argb(255, 0, 0, 0), 0.22f)));
      editor.put("lineNumberPanelText", Colors.hex(text));
      editor.put("completionWndBackground", Colors.hex(bg));
      editor.put("completionWndCorner", Colors.hex(bg));
      editor.put("keyword", Colors.hex(keyword));
      editor.put("comment", Colors.hex(muted));
      editor.put("operator", Colors.hex(operator));
      editor.put("literal", Colors.hex(literal));
      editor.put("identifierVar", Colors.hex(identifierVar));
      editor.put("identifierName", Colors.hex(identifierName));
      editor.put("functionName", Colors.hex(identifierName));
      editor.put("annotation", Colors.hex(annotation));
      editor.put("matchedTextBackground", Colors.hex(stroke));
      editor.put("matchedTextBorder", Colors.hex(accent));
      editor.put("textSelected", Colors.hex(text));
      editor.put("nonPrintableChar", Colors.hex(stroke));
      editor.put("htmlTag", Colors.hex(identifierVar));
      editor.put("attributeName", Colors.hex(literal));
      editor.put("attributeValue", Colors.hex(keyword));
      editor.put("problemError", Colors.hex(error));
      editor.put("problemWarning", Colors.hex(warn));
      editor.put("problemTypo", Colors.hex(typo));
      editor.put("colornextdot", Colors.hex(keyword));
      editor.put("colornextbrak", Colors.hex(operator));
      editor.put("colornextchar", Colors.hex(literal));
      editor.put("coloruppercase", Colors.hex(identifierName));
      editor.put("colornextless", Colors.hex(annotation));
      editor.put("lineNumberCurrent", Colors.hex(accent));
      editor.put("selectedTextBorder", Colors.hex(accent));
      editor.put("currentRowBorder", Colors.hex(stroke));
      editor.put("highlightedDelimitersBackground", Colors.hex(surface));
      editor.put("highlightedDelimitersUnderline", Colors.hex(accent));
      editor.put("highlightedDelimitersForeground", Colors.hex(text));
      editor.put("highlightedDelimitersBorder", Colors.hex(accent));
      editor.put("textHighlightBackground", Colors.hex(stroke));
      editor.put("textHighlightBorder", Colors.hex(accent));
      editor.put("textHighlightStrongBackground", Colors.hex(surface));
      editor.put("textHighlightStrongBorder", Colors.hex(keyword));
      editor.put("staticSpanBackground", Colors.hex(bg));
      editor.put("staticSpanForeground", Colors.hex(text));
      editor.put("textInlayHintBackground", Colors.hex(surface));
      editor.put("textInlayHintForeground", Colors.hex(muted));
      editor.put("snippetBackgroundEditing", Colors.hex(surface));
      editor.put("snippetBackgroundRelated", Colors.hex(stroke));
      editor.put("snippetBackgroundInactive", Colors.hex(Colors.mix(bg, Colors.argb(255, 0, 0, 0), 0.15f)));
      editor.put("hardWrapMarker", Colors.hex(stroke));
      editor.put("functionCharBackgroundStroke", Colors.hex(stroke));
      editor.put("diagnosticTooltipBackground", Colors.hex(surface));
      editor.put("diagnosticTooltipBriefMsg", Colors.hex(text));
      editor.put("diagnosticTooltipDetailedMsg", Colors.hex(muted));
      editor.put("diagnosticTooltipAction", Colors.hex(accent));
      editor.put("stickyScrollDivider", Colors.hex(stroke));
      editor.put("strikeThrough", Colors.hex(Colors.withAlpha(text, 0)));
      editor.put("sideBlockLine", Colors.hex(stroke));
      editor.put("completionWndTextPrimary", Colors.hex(text));
      editor.put("completionWndTextSecondary", Colors.hex(muted));
      editor.put("completionWndItemCurrent", Colors.hex(surface));
      editor.put("completionWndTextMatched", Colors.hex(identifierName));
      editor.put("signatureBackground", Colors.hex(bg));
      editor.put("signatureBorder", Colors.hex(stroke));
      editor.put("signatureTextNormal", Colors.hex(text));
      editor.put("signatureTextHighlightedParameter", Colors.hex(identifierVar));
      editor.put("hoverBackground", Colors.hex(surface));
      editor.put("hoverBorder", Colors.hex(accent));
      editor.put("hoverTextNormal", Colors.hex(text));
      editor.put("hoverTextHighlighted", Colors.hex(identifierName));
      editor.put("textActionWindowBackground", Colors.hex(bg));
      editor.put("textActionWindowIconColor", Colors.hex(text));
      editor.put("minimapBackground", Colors.hex(Colors.withAlpha(bg, 0x28)));
      editor.put("minimapViewport", Colors.hex(Colors.withAlpha(Colors.argb(255, 255, 255, 255), 0x30)));
      editor.put("minimapViewportBorder", Colors.hex(Colors.withAlpha(Colors.argb(255, 255, 255, 255), 0xB0)));
      editor.put("bracketlevelmatch1", Colors.hex(s[0 % s.length]));
      editor.put("bracketlevelmatch2", Colors.hex(s[1 % s.length]));
      editor.put("bracketlevelmatch3", Colors.hex(s[2 % s.length]));
      editor.put("bracketlevelmatch4", Colors.hex(s[3 % s.length]));
      editor.put("bracketlevelmatch5", Colors.hex(s[4 % s.length]));
      editor.put("bracketlevelmatch6", Colors.hex(s[5 % s.length]));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build editor theme", e);
    }

    JSONObject widget = new JSONObject();
    try {
      widget.put("text", Colors.hex(text));
      widget.put("hint", Colors.hex(muted));
      widget.put("accent", Colors.hex(accent));
      widget.put("background", Colors.hex(bg));
      widget.put("surface", Colors.hex(surface));
      widget.put("stroke", Colors.hex(stroke));
      widget.put("fabBackground", Colors.hex(accent));
      widget.put("fabIcon", Colors.hex(fabIcon));
      widget.put("tabSelected", Colors.hex(accent));
      widget.put("tabUnselected", Colors.hex(muted));
      widget.put("imageTint", Colors.hex(text));
      widget.put("menubackground", Colors.hex(bg));
      widget.put("menutextcolor", Colors.hex(text));
      widget.put("selectedmenucolor", Colors.hex(stroke));
      widget.put("imagepath", imagePath == null ? "" : imagePath);
      widget.put("blursize", imagePath == null ? 1 : 18);
    } catch (Exception e) {
      throw new RuntimeException("Failed to build widget theme", e);
    }

    JSONObject activity = new JSONObject();
    try {
      activity.put("background", Colors.hex(bg));
      activity.put("statusBar", Colors.hex(bg));
      activity.put("navigationBar", Colors.hex(bg));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build activity theme", e);
    }

    JSONObject theme = new JSONObject();
    try {
      theme.put("name", name);
      theme.put("activity", activity);
      theme.put("editor", editor);
      theme.put("widget", widget);
    } catch (Exception e) {
      throw new RuntimeException("Failed to build theme", e);
    }
    return theme;
  }

  /** Hex colors of the palette for the preview swatch row. */
  public static String[] swatchHex(Palette p) {
    int[] colors = p.allColors();
    String[] hexes = new String[colors.length];
    for (int i = 0; i < colors.length; i++) {
      hexes[i] = Colors.hex(colors[i]);
    }
    return hexes;
  }
}
