package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.Registration;
import app.ari.api.feature.account.domain.model.User;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Who the users are. Supabase Auth keeps their passwords; the profiles live here. */
public interface AccountService {

  /**
   * Creates a password user with no plan, no email and no birth date yet: a Supabase user with a
   * placeholder email, whose profile the database trigger fills in from its metadata.
   */
  Registration register(String username, String password, String fullName);

  Optional<User> findById(UUID id);

  /**
   * Changes the full name, username and date of birth (null removes it); empty when another account
   * has the username (in any case).
   */
  Optional<User> updateProfile(UUID id, String username, String fullName, LocalDate birthDate);

  /** Adds {@code delta} (negative to take away) to the user's saves count. */
  void changeSavesCount(UUID id, int delta);
}
