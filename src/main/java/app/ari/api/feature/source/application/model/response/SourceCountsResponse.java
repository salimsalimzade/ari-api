package app.ari.api.feature.source.application.model.response;

import app.ari.api.feature.source.domain.model.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * How many sources the Sources page lists: those with saves. {@code kinds} has both kinds, 0
 * included.
 */
public record SourceCountsResponse(
    @Schema(description = "Sources of every kind") int total, List<KindCount> kinds) {

  public record KindCount(SourceKind kind, int count) {}
}
