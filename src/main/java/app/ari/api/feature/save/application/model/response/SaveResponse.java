package app.ari.api.feature.save.application.model.response;

import app.ari.api.feature.save.domain.model.ClipType;
import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.SaveKind;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A saved item with its source. Fields that don't apply are left out: {@code color} for a clip,
 * {@code clipType}, {@code contentMd} and {@code imageUrl} for what isn't that kind of clip, {@code
 * section} and {@code pageNumber} when unknown. {@code imageUrl} shows an image clip for an hour.
 * The link that opens the page at this spot is built from {@code source.url} and {@code text}.
 */
public record SaveResponse(
    UUID id,
    SaveKind kind,
    HighlightColor color,
    ClipType clipType,
    String text,
    String contentMd,
    String imageUrl,
    String comment,
    String section,
    Integer pageNumber,
    @Schema(description = "Alphabetical") List<String> tags,
    SourceResponse source,
    @Schema(description = "When it was saved; the Library groups by day") Instant createdAt) {}
