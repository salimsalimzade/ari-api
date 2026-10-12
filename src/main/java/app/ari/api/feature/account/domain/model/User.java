package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Someone who can sign in. {@code username} is trimmed and lowercase and is what people sign in
 * with; {@code email} is optional and null until it is added, and {@code emailVerifiedAt} is null
 * until it is proven.
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
