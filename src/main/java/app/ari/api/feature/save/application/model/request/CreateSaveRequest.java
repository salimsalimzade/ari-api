package app.ari.api.feature.save.application.model.request;

import app.ari.api.feature.save.domain.model.ClipType;
import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.SaveKind;
import app.ari.api.feature.source.application.model.request.SourceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/saves}, or its {@code save} part when an image clip is sent as
 * multipart. The kind decides what else is needed: a HIGHLIGHT needs a color; a CLIP needs a clip
 * type and no color, and a TABLE clip its Markdown or an IMAGE clip its image.
 */
public record CreateSaveRequest(
    @Schema(description = "HIGHLIGHT or CLIP; IMPORT is only made by importing a file") @NotNull SaveKind kind,
    @Schema(description = "Required for a HIGHLIGHT, not allowed for a CLIP") HighlightColor color,
    @Schema(description = "Required for a CLIP, not allowed for a HIGHLIGHT") ClipType clipType,
    @Schema(
            description = "The marked words, or a clip's caption",
            example = "In late spring a colony splits in two.")
        @NotBlank @Size(max = 10000) String text,
    @Schema(description = "A TABLE clip's table as Markdown, and only for it") @Size(max = 50000) String contentMd,
    @Schema(description = "Left out or empty when there is none", example = "Opening line")
        @Size(max = 2000) String comment,
    @Schema(description = "The heading above it on the page", example = "Why swarms split")
        @Size(max = 500) String section,
    @Schema(description = "Up to 10 tags; names that differ only in case are one tag")
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> tags,
    @NotNull @Valid SourceRequest source) {}
