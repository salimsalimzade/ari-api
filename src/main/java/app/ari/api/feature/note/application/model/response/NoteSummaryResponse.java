package app.ari.api.feature.note.application.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A note as the Notes page lists it, without its text. */
public record NoteSummaryResponse(
    UUID id,
    @Schema(description = "Empty until the note is named") String title,
    @Schema(description = "The saves it quotes, in order") List<UUID> saveIds,
    @Schema(description = "No title, no quote and no paragraph with anything in it") boolean empty,
    Instant createdAt,
    @Schema(description = "The last edit; the list is in this order") Instant updatedAt) {}
