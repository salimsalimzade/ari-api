package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Someone who can sign in. {@code email} and {@code username} are trimmed and lowercase; {@code
 * passwordHash} is null for users who only sign in with Google; {@code birthDate} is null for
 * Google users until they give one; {@code emailVerifiedAt} is null until the email is proven.
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
    Instant updatedAt) {

  /**
   * Google proved this user owns the email. A password set while the email was unverified is
   * removed: whoever registered it never proved they own the address.
   */
  public User verifiedByGoogle(Instant now) {
    if (emailVerifiedAt != null) {
      return this;
    }
    return new User(
        id, email, username, null, fullName, birthDate, plan, savesCount, now, createdAt, now);
  }
}
