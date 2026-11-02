package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Someone who can sign in: the profile of a Supabase Auth user, with the same id (Supabase keeps
 * the password and any Google identity). {@code username} is trimmed and lowercase and is what
 * people sign in with; {@code needsUsername} is true while a Google user hasn't confirmed the one
 * made from their email. {@code email} is optional and only ever a verified address; {@code
 * pendingEmail} waits for its verify link, so a verified email keeps working until a new one is
 * confirmed. Both are lowercase. {@code avatarKey} is the profile photo's key in file storage, null
 * without one.
 */
public record User(
    UUID id,
    String email,
    String pendingEmail,
    String username,
    boolean needsUsername,
    String fullName,
    LocalDate birthDate,
    String avatarKey,
    Plan plan,
    int savesCount,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
