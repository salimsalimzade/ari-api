package app.ari.api.feature.save.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What the Library is narrowed to. Within tags, colors or sources a save needs to match any one of
 * them; across them it needs to match each group that is given. {@code kind} is the type tab. Tag
 * names are compared in lowercase.
 */
public record SaveFilter(
    SaveKind kind, List<String> tags, List<HighlightColor> colors, List<UUID> sourceIds) {

  public SaveFilter {
    tags =
        tags == null
            ? List.of()
            : tags.stream()
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    colors = colors == null ? List.of() : List.copyOf(colors);
    sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
  }

  /** The same filter for every type, for counting the type tabs. */
  public SaveFilter withoutKind() {
    return new SaveFilter(null, tags, colors, sourceIds);
  }
}
