package app.ari.api.feature.save.application.model.request;

import app.ari.api.feature.save.domain.model.HighlightColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PATCH /api/v1/saves/{id}}. A field that is left out stays as it is. An empty
 * {@code comment} clears the comment and an empty list of {@code tags} removes them all.
 */
public record UpdateSaveRequest(
    @Schema(description = "Not allowed for a clip") HighlightColor color,
    @Size(max = 2000) String comment,
    @Schema(description = "Replaces all tags: up to 10, names that differ only in case are one tag")
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> tags) {}
