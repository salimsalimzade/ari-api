package app.ari.api.feature.source.application.model.request;

import app.ari.api.feature.source.domain.model.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/v1/sources}: the kind tab, the cursor of the page before, and
 * the page size.
 */
public record ListSourcesRequest(
    @Schema(description = "The kind tab; left out for all") SourceKind kind,
    @Schema(
            description =
                "Search: words that start words of a source's name, title or author. Accents and"
                    + " case don't count")
        @Size(max = 200) String q,
    @Schema(description = "nextCursor of the page before; left out for the first page")
        String cursor,
    @Schema(description = "Page size, 40 when left out") @Min(1) @Max(100) Integer limit) {

  public static final int DEFAULT_LIMIT = 40;

  public int pageSize() {
    return limit == null ? DEFAULT_LIMIT : limit;
  }
}
