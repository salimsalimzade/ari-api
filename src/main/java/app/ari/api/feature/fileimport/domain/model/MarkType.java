package app.ari.api.feature.fileimport.domain.model;

/**
 * What kind of mark reading found: highlighted, underlined (or squiggly), drawn around (a shape or
 * a freehand line over text), handwritten (a freehand line with no text under it), crossed out, or
 * a note (a sticky note, or a comment on text).
 */
public enum MarkType {
  HIGHLIGHT,
  UNDERLINE,
  DRAWN,
  HANDWRITTEN,
  STRIKE,
  NOTE;

  /** Ticked in the review until the user unticks it: what people mark to keep. */
  public boolean selectedAtFirst() {
    return this == HIGHLIGHT || this == UNDERLINE || this == DRAWN;
  }
}
