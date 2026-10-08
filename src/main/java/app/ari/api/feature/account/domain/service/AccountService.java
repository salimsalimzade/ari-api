package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Who the users are and how they prove it: passwords and Google. */
public interface AccountService {

  /** Creates a password user on the Free plan; empty when the email is already taken. */
  Optional<User> register(String email, String password, String displayName);

  /** Checks an email and password; empty for every mismatch, including Google-only users. */
  Optional<User> authenticate(String email, String password);

  Optional<User> findById(UUID id);

  /**
   * Finds, links or creates the user behind a Google account. Empty when Google sign-in must be
   * refused: Google hasn't verified the email, or the user already has another Google account.
   */
  Optional<User> signInWithGoogle(
      String subject, String email, boolean emailVerified, String displayName);
}
