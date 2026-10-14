package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.common.client.ObjectStorage;
import app.ari.api.common.image.ImageFormat;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.PhotoService;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A new photo gets a new key, so a browser never shows a cached old one. The file is stored before
 * the row points at it, and the old file is deleted only after.
 */
@Service
public class PhotoServiceImpl implements PhotoService {

  private static final Duration LINK_LIFETIME = Duration.ofHours(1);

  private final UserRepository users;
  private final ObjectStorage storage;
  private final Clock clock;

  public PhotoServiceImpl(UserRepository users, ObjectStorage storage, Clock clock) {
    this.users = users;
    this.storage = storage;
    this.clock = clock;
  }

  @Override
  public Optional<User> changePhoto(User user, byte[] photo) {
    Optional<ImageFormat> format = ImageFormat.of(photo);
    if (format.isEmpty() || photo.length > MAX_BYTES) {
      return Optional.empty();
    }
    String key = "avatars/" + user.id() + "/" + UUID.randomUUID() + "." + format.get().extension();
    storage.put(key, photo, format.get().contentType());
    try {
      users.setAvatarKey(user.id(), key, clock.instant());
    } catch (RuntimeException e) {
      storage.deleteQuietly(key);
      throw e;
    }
    if (user.avatarKey() != null) {
      storage.deleteQuietly(user.avatarKey());
    }
    return users.findById(user.id());
  }

  @Override
  public void removePhoto(User user) {
    if (user.avatarKey() == null) {
      return;
    }
    users.setAvatarKey(user.id(), null, clock.instant());
    storage.deleteQuietly(user.avatarKey());
  }

  @Override
  public Optional<String> photoUrl(User user) {
    return Optional.ofNullable(user.avatarKey()).map(key -> storage.signedUrl(key, LINK_LIFETIME));
  }
}
