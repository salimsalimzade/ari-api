package app.ari.api.feature.account.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone who can sign in. {@code email} is trimmed and lowercase; {@code passwordHash} is null for
 * users who only sign in with Google; {@code emailVerifiedAt} is null until the email is proven.
 */
public record User(
    UUID id,
    String email,
    String passwordHash,
    String displayName,
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
    return new User(id, email, null, displayName, plan, savesCount, now, createdAt, now);
  }
}
