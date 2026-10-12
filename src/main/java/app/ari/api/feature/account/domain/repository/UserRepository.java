package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Stores users. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  /** Looks up a normalized (trimmed, lowercase) username. */
  Optional<User> findByUsername(String username);

  /** Adds the user; returns false and changes nothing when the username is already taken. */
  boolean insert(User user);

  void update(User user);
}
