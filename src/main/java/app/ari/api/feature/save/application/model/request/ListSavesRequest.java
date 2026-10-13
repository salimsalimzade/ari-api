package app.ari.api.feature.save.application.model.request;

import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.SaveKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/saves}: the filters of {@link SaveFilterRequest}, the
 * cursor of the page before, and the page size.
 */
public record ListSavesRequest(
    @Schema(description = "The type tab; left out for all") SaveKind type,
    @Schema(description = "Tag names, in any case") List<String> tag,
    List<HighlightColor> color,
    @Schema(description = "Source ids") List<UUID> source,
    @Schema(description = "nextCursor of the page before; left out for the first page")
        String cursor,
    @Schema(description = "Page size, 40 when left out") @Min(1) @Max(100) Integer limit) {

  public static final int DEFAULT_LIMIT = 40;

  public int pageSize() {
    return limit == null ? DEFAULT_LIMIT : limit;
  }
}
