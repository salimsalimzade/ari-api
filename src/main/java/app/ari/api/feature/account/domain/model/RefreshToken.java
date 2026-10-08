package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored refresh token. Only the SHA-256 hash of the token is kept. Every refresh revokes the
 * token and issues a new one with the same {@code familyId}, so one family is one session.
 */
public record RefreshToken(
    UUID id,
    UUID userId,
    String tokenHash,
    UUID familyId,
    TokenClient client,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt) {}
