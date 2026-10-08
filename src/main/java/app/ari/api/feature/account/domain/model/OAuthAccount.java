package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A provider account linked to a user. {@code providerUserId} is the provider's stable id (Google's
 * {@code sub}); {@code email} is the address the provider reported when the link was made.
 */
public record OAuthAccount(
    UUID id,
    UUID userId,
    AuthProvider provider,
    String providerUserId,
    String email,
    Instant createdAt) {}
