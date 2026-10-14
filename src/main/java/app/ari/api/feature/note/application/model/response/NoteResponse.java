package app.ari.api.feature.note.application.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A note with its blocks in order; each quote carries its save, with source and tags. */
public record NoteResponse(
    UUID id,
    @Schema(description = "Empty until the note is named") String title,
    List<NoteBlockResponse> blocks,
    Instant createdAt,
    @Schema(description = "The last edit; the same as createdAt until the first one")
        Instant updatedAt) {}
