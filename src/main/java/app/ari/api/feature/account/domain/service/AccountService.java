package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.User;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Who the users are and how they prove it: passwords and Google. */
public interface AccountService {

  /**
   * Creates a password user on the Free plan; empty when the email or the username is already taken
   * (see {@link #usernameTaken}).
   */
  Optional<User> register(
      String email, String username, String password, String fullName, LocalDate birthDate);

  /** Whether the username, compared after trimming and lowercasing, already belongs to a user. */
  boolean usernameTaken(String username);

  /** Checks an email and password; empty for every mismatch, including Google-only users. */
  Optional<User> authenticate(String email, String password);

  Optional<User> findById(UUID id);

  /**
   * Finds, links or creates the user behind a Google account; a new user gets a username made from
   * the email. Empty when Google sign-in must be refused: Google hasn't verified the email, or the
   * user already has another Google account.
   */
  Optional<User> signInWithGoogle(
      String subject, String email, boolean emailVerified, String fullName);
}
