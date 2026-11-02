package app.ari.api.feature.account.domain.port;

import app.ari.api.feature.account.domain.model.IssuedTokens;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Supabase Auth: the accounts' passwords, Google identities and sessions. Users sign in there by
 * email; an account without one has a placeholder ({@code NoEmail}). Every Supabase user gets a
 * users row from the database trigger, so an id here is also a profile's id.
 */
public interface IdentityProvider {

  enum Outcome {
    DONE,
    /** The id or the email belongs to another user, or the profile trigger refused the row. */
    TAKEN,
    /** Supabase's password rules refused it. */
    WEAK_PASSWORD
  }

  /**
   * Creates a confirmed user with this id, email and password; {@code metadata} becomes its user
   * metadata, which the profile trigger reads ({@code username}, {@code full_name}, {@code
   * birth_date}).
   */
  Outcome createUser(UUID id, String email, String password, Map<String, String> metadata);

  /** A new session; empty when the email and password don't match. */
  Optional<IssuedTokens> signIn(String email, String password);

  /** The session's next tokens; empty when the refresh token is unknown, used up or revoked. */
  Optional<IssuedTokens> refresh(String refreshToken);

  /**
   * A new session for the user without their password (through a one-time magic-link token that is
   * never emailed); empty when the user is gone.
   */
  Optional<IssuedTokens> newSession(UUID userId);

  /** Where to send the browser for Google, which comes back to {@code redirectTo}. */
  URI googleSignInUrl(String redirectTo, String codeChallenge);

  /** Finishes a Google sign-in; empty when the code is unknown, used or another browser's. */
  Optional<IssuedTokens> exchangeCode(String authCode, String codeVerifier);

  /**
   * Ends the access token's session, or every session of its user; false when the token no longer
   * works (it expired, or its session already ended).
   */
  boolean signOut(String accessToken, boolean everywhere);

  /** The user's sign-in email (a placeholder until they verify their own); empty when gone. */
  Optional<String> email(UUID userId);

  /** Makes it the user's confirmed sign-in email; TAKEN when another user has it. */
  Outcome setEmail(UUID userId, String email);

  /** Sets the user's password; their sessions go on. */
  Outcome setPassword(UUID userId, String password);
}
