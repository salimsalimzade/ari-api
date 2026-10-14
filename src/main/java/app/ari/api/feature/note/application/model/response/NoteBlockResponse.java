package app.ari.api.feature.note.application.model.response;

import app.ari.api.feature.note.domain.model.NoteBlockKind;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** A paragraph's text, or the save a quote shows, as it is now. */
public record NoteBlockResponse(
    NoteBlockKind kind,
    @Schema(description = "TEXT only") String text,
    @Schema(description = "QUOTE only") SaveResponse save) {}
