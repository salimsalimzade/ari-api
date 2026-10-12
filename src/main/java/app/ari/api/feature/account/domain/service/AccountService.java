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
}
