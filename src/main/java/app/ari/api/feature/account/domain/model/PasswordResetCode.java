package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.util.UUID;

/** What a reset link stands for: a new password for this user, until {@code expiresAt}. */
public record PasswordResetCode(UUID userId, Instant expiresAt) {}
