package app.ari.api.feature.save.application.model.request;

import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.SaveKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The Library's filters as query parameters; repeat a parameter to give several values ({@code
 * ?tag=a&tag=b}). Within a parameter a save matches any value, across parameters each one.
 */
public record SaveFilterRequest(
    @Schema(description = "The type tab; left out for all") SaveKind type,
    @Schema(description = "Tag names, in any case") List<String> tag,
    List<HighlightColor> color,
    @Schema(description = "Source ids") List<UUID> source) {}
