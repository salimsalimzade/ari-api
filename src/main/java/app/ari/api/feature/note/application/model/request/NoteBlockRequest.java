package app.ari.api.feature.note.application.model.request;

import app.ari.api.feature.note.domain.model.NoteBlockKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A block of a note: a TEXT block sends its text, a QUOTE block the id of the save it quotes. */
public record NoteBlockRequest(
    @NotNull NoteBlockKind kind,
    @Schema(description = "TEXT only; empty for a blank paragraph") @Size(max = 20000) String text,
    @Schema(description = "QUOTE only: one of the user's saves") UUID saveId) {}
