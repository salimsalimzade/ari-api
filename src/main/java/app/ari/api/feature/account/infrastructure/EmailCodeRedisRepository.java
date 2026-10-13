package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.model.EmailCode;
import app.ari.api.feature.account.domain.repository.EmailCodeRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Redis adapter for {@link EmailCodeRepository}: {@code code:verify-email:{hash}} holds JSON. */
@Repository
public class EmailCodeRedisRepository implements EmailCodeRepository {

  private static final String PREFIX = "code:verify-email:";

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;

  public EmailCodeRedisRepository(StringRedisTemplate redis, JsonMapper jsonMapper) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void save(String codeHash, EmailCode code, Duration lifetime) {
    redis.opsForValue().set(PREFIX + codeHash, jsonMapper.writeValueAsString(code), lifetime);
  }

  @Override
  public Optional<EmailCode> take(String codeHash) {
    return Optional.ofNullable(redis.opsForValue().getAndDelete(PREFIX + codeHash))
        .map(json -> jsonMapper.readValue(json, EmailCode.class));
  }
}
