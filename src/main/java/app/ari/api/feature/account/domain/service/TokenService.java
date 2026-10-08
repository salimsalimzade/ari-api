package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.TokenClient;
import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;

/** Access tokens (JWT, 15 minutes) and rotating refresh tokens (30 days). */
public interface TokenService {

  /** The {@code iss} claim of every access token; the decoder rejects any other issuer. */
  String ISSUER = "ari-api";

  /** Starts a new session for the user. */
  IssuedTokens issue(User user, TokenClient client);

  /**
   * Swaps a refresh token for a new pair in the same session. Empty when the token is unknown,
   * expired, or revoked; reusing a replaced token revokes the whole session.
   */
  Optional<IssuedTokens> rotate(String refreshToken);

  /** Ends the session the refresh token belongs to; unknown tokens are ignored. */
  void revokeSession(String refreshToken);
}
