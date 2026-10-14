package app.ari.api.feature.fileimport.domain.model;

/**
 * A file's color as one of the five, and the file's own when that was a different one.
 *
 * @param fileColor ORANGE or RED when the color was mapped from one of those, else null
 */
public record MappedColor(MarkColor color, FileColor fileColor) {

  public static final MappedColor DEFAULT = new MappedColor(MarkColor.YELLOW, null);

  /**
   * The nearest of the five by hue, for red, green and blue between 0 and 1. Grays, white and
   * black, which tell nothing, become yellow, the usual highlight.
   */
  public static MappedColor ofRgb(float red, float green, float blue) {
    float max = Math.max(red, Math.max(green, blue));
    float min = Math.min(red, Math.min(green, blue));
    float chroma = max - min;
    if (max <= 0.05f || chroma / max < 0.15f) {
      return DEFAULT;
    }
    float hue;
    if (max == red) {
      hue = 60 * (((green - blue) / chroma) % 6);
    } else if (max == green) {
      hue = 60 * (((blue - red) / chroma) + 2);
    } else {
      hue = 60 * (((red - green) / chroma) + 4);
    }
    if (hue < 0) {
      hue += 360;
    }
    if (hue < 15 || hue >= 345) {
      return new MappedColor(MarkColor.PINK, FileColor.RED);
    }
    if (hue < 42) {
      return new MappedColor(MarkColor.YELLOW, FileColor.ORANGE);
    }
    if (hue < 75) {
      return DEFAULT;
    }
    if (hue < 170) {
      return new MappedColor(MarkColor.GREEN, null);
    }
    if (hue < 250) {
      return new MappedColor(MarkColor.BLUE, null);
    }
    if (hue < 290) {
      return new MappedColor(MarkColor.PURPLE, null);
    }
    return new MappedColor(MarkColor.PINK, null);
  }

  /** Whether a hex color has a hue at all: grays, white and black don't. */
  public static boolean hasHue(String hex) {
    String digits = hex == null ? "" : hex.strip().replace("#", "");
    if (!digits.matches("[0-9A-Fa-f]{6}")) {
      return false;
    }
    int value = Integer.parseInt(digits, 16);
    int red = (value >> 16) & 0xFF;
    int green = (value >> 8) & 0xFF;
    int blue = value & 0xFF;
    int max = Math.max(red, Math.max(green, blue));
    int min = Math.min(red, Math.min(green, blue));
    return max > 12 && (max - min) / (float) max >= 0.15f;
  }

  /** A hex color such as {@code FFFF00} or {@code #ffff00}; anything else is the default. */
  public static MappedColor ofHex(String hex) {
    String digits = hex == null ? "" : hex.strip().replace("#", "");
    if (!digits.matches("[0-9A-Fa-f]{6}")) {
      return DEFAULT;
    }
    int value = Integer.parseInt(digits, 16);
    return ofRgb(
        ((value >> 16) & 0xFF) / 255f, ((value >> 8) & 0xFF) / 255f, (value & 0xFF) / 255f);
  }
}
