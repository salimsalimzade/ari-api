package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores refresh tokens by the hash of their value. */
public interface RefreshTokenRepository {

  void insert(RefreshToken token);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Revokes the token if it is still active. Returns false when it was already revoked, which is
   * how two requests refreshing the same token at once find out about each other.
   */
  boolean revoke(UUID id, Instant at);

  /** Revokes every active token of one session. */
  void revokeFamily(UUID familyId, Instant at);

  /** Revokes every active token of the user, signing them out everywhere. */
  void revokeAllForUser(UUID userId, Instant at);

  /** True while the session has a token that is neither revoked nor expired. */
  boolean hasActiveToken(UUID familyId, Instant now);
}
