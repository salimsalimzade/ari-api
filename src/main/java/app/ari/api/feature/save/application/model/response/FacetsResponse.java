package app.ari.api.feature.save.application.model.response;

import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.SaveKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What the Library's filters offer. {@code types} and {@code total} count the saves that match the
 * tag, color and source filters of the request (every type is listed, 0 included). {@code tags},
 * {@code colors} (all five, 0 included) and {@code sources} count over the whole Library, most used
 * first, so choosing one doesn't make the others disappear.
 */
public record FacetsResponse(
    @Schema(description = "Saves of every type that match the filters") int total,
    List<TypeCount> types,
    List<TagFacet> tags,
    List<ColorFacet> colors,
    List<SourceFacet> sources) {

  public record TypeCount(SaveKind kind, int count) {}

  public record TagFacet(String name, int count) {}

  public record ColorFacet(HighlightColor color, int count) {}

  public record SourceFacet(UUID id, String name, int count) {}
}
