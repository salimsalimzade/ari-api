package app.ari.api.feature.note.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/notes} and {@code PUT /api/v1/notes/{id}}: the whole note, which
 * replaces what was there. A quote of a save that isn't the user's (deleted, or never theirs) is
 * left out.
 */
public record NoteRequest(
    @Schema(
            description = "Trimmed; empty or left out until the note is named",
            example = "Swarm decisions")
        @Size(max = 200) String title,
    @Schema(description = "Paragraphs and quotes, in order") @NotNull @Size(max = 1000) List<@NotNull @Valid NoteBlockRequest> blocks) {}
