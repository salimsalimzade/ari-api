package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.TokenClient;
import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Access tokens (JWT, 15 minutes) and rotating refresh tokens (30 days). */
public interface TokenService {

  /** The {@code iss} claim of every access token; the decoder rejects any other issuer. */
  String ISSUER = "ari-api";

  /** Starts a new session for the user. */
  IssuedTokens issue(User user, TokenClient client);

  /**
   * Swaps a refresh token for a new pair in the same session. Empty when the token is unknown,
   * expired, revoked or another client's; reusing a replaced token revokes the whole session.
   */
  Optional<IssuedTokens> rotate(String refreshToken, TokenClient client);

  /** Ends the session the refresh token belongs to; unknown tokens are ignored. */
  void revokeSession(String refreshToken);

  /** A code that signs the browser extension in as the user once, within 60 seconds. */
  String issueExtensionCode(UUID userId);

  /**
   * Uses up the code and starts an extension session for its user. Empty when the code is unknown,
   * expired or used, or its user is gone.
   */
  Optional<IssuedTokens> redeemExtensionCode(String code);
}
