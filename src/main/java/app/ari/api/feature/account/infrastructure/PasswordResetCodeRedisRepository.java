package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.model.PasswordResetCode;
import app.ari.api.feature.account.domain.repository.PasswordResetCodeRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis adapter for {@link PasswordResetCodeRepository}: {@code code:password-reset:{hash}} holds
 * JSON, and {@code code:password-reset-used:{hash}} exists once the link was used.
 */
@Repository
public class PasswordResetCodeRedisRepository implements PasswordResetCodeRepository {

  private static final String PREFIX = "code:password-reset:";
  private static final String USED_PREFIX = "code:password-reset-used:";

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;

  public PasswordResetCodeRedisRepository(StringRedisTemplate redis, JsonMapper jsonMapper) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void save(String codeHash, PasswordResetCode code, Duration keptFor) {
    redis.opsForValue().set(PREFIX + codeHash, jsonMapper.writeValueAsString(code), keptFor);
  }

  @Override
  public Optional<PasswordResetCode> find(String codeHash) {
    return Optional.ofNullable(redis.opsForValue().get(PREFIX + codeHash))
        .map(json -> jsonMapper.readValue(json, PasswordResetCode.class));
  }

  @Override
  public boolean isUsed(String codeHash) {
    return Boolean.TRUE.equals(redis.hasKey(USED_PREFIX + codeHash));
  }

  @Override
  public boolean markUsed(String codeHash, Duration keptFor) {
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(USED_PREFIX + codeHash, "used", keptFor));
  }
}
