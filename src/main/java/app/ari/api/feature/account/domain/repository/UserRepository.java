package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Stores users. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  /** Looks up a normalized (trimmed, lowercase) email. */
  Optional<User> findByEmail(String email);

  /** Adds the user; returns false and changes nothing when the email is already taken. */
  boolean insert(User user);

  void update(User user);
}
