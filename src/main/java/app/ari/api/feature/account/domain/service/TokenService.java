package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.GoogleSignIn;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessions, which Supabase Auth keeps: an access token (a JWT, 15 minutes with the project's
 * settings) and a refresh token that Supabase replaces on every refresh. ari-api hands them to the
 * website as cookies and to the extension as JSON.
 */
public interface TokenService {

  /**
   * Starts a session for the account with this username and password; empty for every mismatch.
   * Unknown usernames are checked against Supabase too, so the answer takes as long.
   */
  Optional<IssuedTokens> signIn(String username, String password);

  /** The session's next tokens; empty when the refresh token is unknown, used up or revoked. */
  Optional<IssuedTokens> rotate(String refreshToken);

  /**
   * Ends the session of the access token, or of the refresh token when the access token is gone or
   * expired; unknown tokens are ignored.
   */
  void revokeSession(String accessToken, String refreshToken);

  /** A code that signs the browser extension in as the user once, within 60 seconds. */
  String issueExtensionCode(UUID userId);

  /**
   * Uses up the code and starts a session of its own for the extension, as its user. Empty when the
   * code is unknown, expired or used, or its user is gone.
   */
  Optional<IssuedTokens> redeemExtensionCode(String code);

  /** Starts a Google sign-in that comes back to {@code redirectTo}. */
  GoogleSignIn startGoogleSignIn(String redirectTo);

  /** Finishes it; empty when the code is unknown or used, or the verifier isn't its own. */
  Optional<IssuedTokens> finishGoogleSignIn(String authCode, String codeVerifier);
}
