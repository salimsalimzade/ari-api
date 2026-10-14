package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;

/** The profile photo, kept in file storage under {@code avatars/{userId}/}. */
public interface PhotoService {

  /** The largest photo: 2 MB. */
  int MAX_BYTES = 2 * 1024 * 1024;

  /**
   * Stores the photo, makes it the user's and deletes the old one. Empty when it isn't a JPEG, PNG
   * or WebP image of at most 2 MB.
   */
  Optional<User> changePhoto(User user, byte[] photo);

  void removePhoto(User user);

  /** A link the browser can show the photo from for an hour; empty without a photo. */
  Optional<String> photoUrl(User user);
}
