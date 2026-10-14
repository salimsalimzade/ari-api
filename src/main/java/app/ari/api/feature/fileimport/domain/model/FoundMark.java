package app.ari.api.feature.fileimport.domain.model;

/**
 * A mark as a reader found it, in the file's order.
 *
 * @param text the marked words, a note's text, or empty for a handwritten mark
 * @param pageNumber from 1, or null when the file doesn't tell
 * @param comment the file's comment on it, or null
 * @param picture a PNG of the spot for a drawn or handwritten mark, or null
 */
// The picture's bytes pass through once; the record is never compared.
@SuppressWarnings("ArrayRecordComponent")
public record FoundMark(
    MarkType type,
    MappedColor color,
    String text,
    Integer pageNumber,
    String comment,
    byte[] picture) {}
