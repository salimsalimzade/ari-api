package app.ari.api.feature.save.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One saved item. {@code color} is set for everything but a clip. A clip has a {@code clipType}:
 * TABLE keeps its Markdown in {@code contentMd}, IMAGE keeps its file in storage under {@code
 * imageKey}. {@code text} is the marked words, or a clip's caption; {@code section} the heading
 * above it on the page; {@code pageNumber} where it was in an imported file. {@code createdAt} is
 * when it was saved, which the Library groups by day.
 */
public record Save(
    UUID id,
    UUID userId,
    UUID sourceId,
    SaveKind kind,
    HighlightColor color,
    ClipType clipType,
    String text,
    String contentMd,
    String imageKey,
    String comment,
    String section,
    Integer pageNumber,
    Instant createdAt,
    Instant updatedAt) {}
