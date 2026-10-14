package app.ari.api.feature.note.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Query parameters of {@code GET /api/v1/notes}. */
public record ListNotesRequest(
    @Schema(
            description =
                "Search: words that start words of a note's title or paragraphs, or part of them."
                    + " Accents and case don't count; left out for every note")
        @Size(max = 200) String q) {}
