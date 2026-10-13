package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  /** Sets or clears the email that waits for its verify link. */
  void setPendingEmail(UUID id, String pendingEmail, Instant at);

  /**
   * Makes {@code email} the verified email if it is still the user's pending one; false when it
   * isn't. Throws {@code DuplicateKeyException} when another account verified it first.
   */
  boolean confirmEmail(UUID id, String email, Instant at);

  /** True when another account has this verified email. */
  boolean isEmailTaken(String email, UUID exceptUserId);

  /** Clears the verified and the pending email. */
  void removeEmail(UUID id, Instant at);

  /**
   * Sets the profile fields; {@code username} is normalized. Throws {@code DuplicateKeyException}
   * when another account has the username.
   */
  void updateProfile(UUID id, String username, String fullName, LocalDate birthDate, Instant at);

  /** Sets or clears the profile photo's storage key. */
  void setAvatarKey(UUID id, String avatarKey, Instant at);

  /**
   * Adds {@code delta} (negative to take away) to the user's saves count in one statement, so saves
   * at the same time never lose one. The count can't go below 0: the database refuses. Leaves
   * {@code updated_at} alone, because it only tracks username changes.
   */
  void changeSavesCount(UUID id, int delta);

  /** Looks up a normalized username. */
  boolean usernameExists(String username);

  /** Up to {@code limit} usernames after {@code after}, in order: one page of a full scan. */
  List<String> findUsernamesAfter(String after, int limit);

  List<String> findUsernamesUpdatedSince(Instant since);
}
