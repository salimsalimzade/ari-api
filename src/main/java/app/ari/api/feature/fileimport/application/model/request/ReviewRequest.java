package app.ari.api.feature.fileimport.application.model.request;

import app.ari.api.feature.fileimport.domain.model.MarkColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Body of {@code PUT /api/v1/imports/{id}/review}. */
public record ReviewRequest(
    @Schema(description = "The source's title; left out or blank keeps the file's name")
        @Size(max = 500) String title,
    @Schema(description = "Left out or blank for none") @Size(max = 255) String author,
    @Schema(description = "A tag for every mark saved now") @Size(max = 50) String tag,
    @Schema(description = "One color for every mark; left out keeps each mark's") MarkColor color,
    @Schema(description = "Every ticked mark: new ones are saved, unticked saved ones removed")
        @NotNull @Size(max = 5000) List<@NotNull UUID> selectedMarkIds) {}
