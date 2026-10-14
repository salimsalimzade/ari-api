package app.ari.api.feature.note.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A note without its blocks. {@code title} is empty until the user names it. {@code updatedAt} is
 * the last edit, which orders the Notes page; it equals {@code createdAt} until the first one.
 */
public record Note(UUID id, UUID userId, String title, Instant createdAt, Instant updatedAt) {}
