package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Someone who can sign in. {@code email} and {@code username} are trimmed and lowercase; {@code
 * emailVerifiedAt} is null until the email is proven.
 */
public record User(
    UUID id,
    String email,
    String username,
    String passwordHash,
    String fullName,
    LocalDate birthDate,
    Plan plan,
    int savesCount,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
