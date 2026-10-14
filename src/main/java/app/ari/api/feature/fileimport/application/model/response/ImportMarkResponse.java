package app.ari.api.feature.fileimport.application.model.response;

import app.ari.api.feature.fileimport.domain.model.FileColor;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Something reading found in a file, to tick or untick in the review. */
public record ImportMarkResponse(
    UUID id,
    MarkType type,
    @Schema(description = "One of the five; for a highlight, its color mapped from the file's")
        MarkColor color,
    @Schema(description = "The file's own color, when it had to be mapped: ORANGE or RED")
        FileColor fileColor,
    @Schema(description = "The marked words or a note's text; empty for a handwritten mark")
        String text,
    @Schema(description = "From 1; left out when the file doesn't tell") Integer pageNumber,
    @Schema(description = "The file's comment on the mark") String comment,
    @Schema(description = "A picture of a drawn or handwritten mark, signed for an hour")
        String imageUrl,
    @Schema(description = "Ticked in the review") boolean selected,
    @Schema(description = "The save it became; left out until it is saved") UUID saveId) {}
