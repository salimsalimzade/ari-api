package app.ari.api.feature.save.domain.model;

import java.util.List;
import java.util.Map;

/**
 * The choices the Library's filters offer. {@code kinds} counts the saves of each type among those
 * that match the tag, color and source filters (every type is there, 0 included); {@code tags},
 * {@code colors} (all five, 0 included) and {@code sources} count over the whole Library, so
 * choosing one doesn't make the others disappear. Tags and sources are most used first.
 */
public record SaveFacets(
    Map<SaveKind, Integer> kinds,
    List<TagCount> tags,
    Map<HighlightColor, Integer> colors,
    List<SourceCount> sources) {}
