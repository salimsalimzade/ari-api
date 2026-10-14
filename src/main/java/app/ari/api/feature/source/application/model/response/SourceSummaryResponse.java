package app.ari.api.feature.source.application.model.response;

import app.ari.api.feature.source.domain.model.SourceKind;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A source with what is saved from it, for the Sources page and a source's own page. {@code url} is
 * left out for a file, {@code author} when unknown, and {@code latestHighlight} when nothing saved
 * from it has a color (only clips).
 */
public record SourceSummaryResponse(
    UUID id,
    SourceKind kind,
    @Schema(description = "The site's name, or the file's") String name,
    String title,
    String author,
    String url,
    @Schema(description = "Every save from it") int savesCount,
    int highlightsCount,
    int clipsCount,
    @Schema(description = "Highlights saved from an imported file") int importsCount,
    @ArraySchema(
            arraySchema = @Schema(description = "The colors its saves use, most used first"),
            schema = @Schema(allowableValues = {"YELLOW", "GREEN", "BLUE", "PINK", "PURPLE"}))
        List<String> colors,
    LatestHighlight latestHighlight,
    @Schema(description = "The notes quoting its saves, the one changed most recently first")
        List<QuotingNote> notes,
    @Schema(description = "When the first save from it was made") Instant firstSavedAt,
    @Schema(description = "When the latest save from it was made; the list is in this order")
        Instant lastSavedAt) {

  /** A note that quotes saves from the source, and how many of them. */
  public record QuotingNote(
      UUID id,
      @Schema(description = "Empty until the note is named") String title,
      int quotesCount) {}

  /** The newest save from the source that has a color, for its card. */
  public record LatestHighlight(
      String text,
      @Schema(allowableValues = {"YELLOW", "GREEN", "BLUE", "PINK", "PURPLE"}) String color) {}
}
