package app.ari.api.feature.source.domain.model;

import java.util.UUID;

/** A note that quotes saves from a source, and how many of them. */
public record SourceNote(UUID sourceId, UUID noteId, String title, int quotesCount) {}
