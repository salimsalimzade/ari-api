package app.ari.api.feature.save.application.model.request;

import java.util.List;

/**
 * A mark from an imported file to save, for the import feature's review. Without an image it is an
 * IMPORT save with its {@code color} (YELLOW, GREEN, BLUE, PINK or PURPLE); with one it is an image
 * clip of that picture (a handwritten mark), with {@code text} as its caption.
 *
 * @param pageNumber where it is in the file, or null
 * @param image a JPEG, PNG or WebP of at most 2 MB, or null
 */
// The picture's bytes pass through once; the record is never compared.
@SuppressWarnings("ArrayRecordComponent")
public record ImportedSaveRequest(
    String color,
    String text,
    String comment,
    Integer pageNumber,
    List<String> tags,
    byte[] image) {}
