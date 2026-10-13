package app.ari.api.feature.save.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A label a user puts on saves; "Swarm" and "swarm" are one tag. */
public record Tag(UUID id, UUID userId, String name, Instant createdAt) {}
