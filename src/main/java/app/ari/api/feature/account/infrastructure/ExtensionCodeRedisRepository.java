package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.repository.ExtensionCodeRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis adapter for {@link ExtensionCodeRepository}: {@code code:extension:{hash}} holds the user
 * id.
 */
@Repository
public class ExtensionCodeRedisRepository implements ExtensionCodeRepository {

  private static final String PREFIX = "code:extension:";

  private final StringRedisTemplate redis;

  public ExtensionCodeRedisRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void save(String codeHash, UUID userId, Duration lifetime) {
    redis.opsForValue().set(PREFIX + codeHash, userId.toString(), lifetime);
  }

  @Override
  public Optional<UUID> take(String codeHash) {
    return Optional.ofNullable(redis.opsForValue().getAndDelete(PREFIX + codeHash))
        .map(UUID::fromString);
  }
}
