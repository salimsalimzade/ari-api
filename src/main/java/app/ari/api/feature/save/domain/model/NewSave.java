package app.ari.api.feature.save.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * What a client sends to save something. Text is trimmed, a blank section is left out, and the tags
 * are normalized (see {@link Tags}).
 */
public record NewSave(
    SaveKind kind,
    HighlightColor color,
    ClipType clipType,
    String text,
    String contentMd,
    String comment,
    String section,
    List<String> tags) {

  public NewSave {
    text = text == null ? "" : text.strip();
    comment = comment == null ? "" : comment.strip();
    section = section == null || section.isBlank() ? null : section.strip();
    tags = Tags.normalize(tags);
  }

  /**
   * What stops this from being saved, if anything: the kind decides which of color, clip type,
   * Markdown and image must be there and which must not. {@code hasImage} says whether an image was
   * sent with it.
   */
  public Optional<String> violation(boolean hasImage) {
    if (kind == SaveKind.IMPORT) {
      return Optional.of("An import is made by importing a file, not saved directly");
    }
    if (kind == SaveKind.HIGHLIGHT) {
      if (color == null) {
        return Optional.of("A highlight needs a color");
      }
      if (clipType != null || contentMd != null || hasImage) {
        return Optional.of("A highlight has no clip type, Markdown or image");
      }
      return Optional.empty();
    }
    if (color != null) {
      return Optional.of("A clip has no color");
    }
    if (clipType == null) {
      return Optional.of("A clip needs a clip type, TABLE or IMAGE");
    }
    return switch (clipType) {
      case TABLE -> tableViolation(hasImage);
      case IMAGE -> imageViolation(hasImage);
    };
  }

  private Optional<String> tableViolation(boolean hasImage) {
    if (contentMd == null || contentMd.isBlank()) {
      return Optional.of("A table clip needs its Markdown in contentMd");
    }
    return hasImage ? Optional.of("A table clip has no image") : Optional.empty();
  }

  private Optional<String> imageViolation(boolean hasImage) {
    if (!hasImage) {
      return Optional.of("An image clip needs its image, sent as the image part");
    }
    return contentMd != null ? Optional.of("An image clip has no Markdown") : Optional.empty();
  }
}
