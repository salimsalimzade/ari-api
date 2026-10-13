package app.ari.api.feature.save.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * An edit to a save. A null field is left as it is; an empty comment clears it, and an empty list
 * of tags removes them all.
 */
public record SaveChange(HighlightColor color, String comment, List<String> tags) {

  public SaveChange {
    comment = comment == null ? null : comment.strip();
    tags = tags == null ? null : Tags.normalize(tags);
  }

  /** What stops this edit from being made to {@code save}, if anything. */
  public Optional<String> violationFor(Save save) {
    if (color != null && save.kind() == SaveKind.CLIP) {
      return Optional.of("A clip has no color");
    }
    return Optional.empty();
  }
}
