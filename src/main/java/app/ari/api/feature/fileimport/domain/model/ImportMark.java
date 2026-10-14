package app.ari.api.feature.fileimport.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Something reading found in a file, at {@code position} in the file's order. {@code saveId} is the
 * save it became, null until the review saves it (and again when the save is deleted). {@code
 * imageKey} is a picture of the spot, for a drawn or handwritten mark. {@code selected} is whether
 * it is ticked in the review.
 */
public record ImportMark(
    UUID id,
    UUID importId,
    UUID saveId,
    int position,
    MarkType type,
    MarkColor color,
    FileColor fileColor,
    String text,
    Integer pageNumber,
    String comment,
    String imageKey,
    boolean selected,
    Instant createdAt) {}
