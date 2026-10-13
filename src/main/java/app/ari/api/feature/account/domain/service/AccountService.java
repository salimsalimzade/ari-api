package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.User;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Who the users are and how they prove it: passwords. */
public interface AccountService {

  /** Creates a password user with no plan yet; empty when the username is already taken. */
  Optional<User> register(String username, String password, String fullName, LocalDate birthDate);

  /** Checks a username and password; empty for every mismatch. */
  Optional<User> authenticate(String username, String password);

  Optional<User> findById(UUID id);

  /**
   * Changes the full name, username and date of birth; empty when another account has the username
   * (in any case).
   */
  Optional<User> updateProfile(UUID id, String username, String fullName, LocalDate birthDate);

  /** Adds {@code delta} (negative to take away) to the user's saves count. */
  void changeSavesCount(UUID id, int delta);
}
