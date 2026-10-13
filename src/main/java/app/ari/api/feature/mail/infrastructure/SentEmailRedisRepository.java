package app.ari.api.feature.mail.infrastructure;

import app.ari.api.feature.mail.domain.model.SendClaim;
import app.ari.api.feature.mail.domain.repository.SentEmailRepository;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis adapter for {@link SentEmailRepository}: {@code idem:mail:{event id}} holds {@code sending}
 * for a minute while a sender works, then {@code sent} for a week.
 */
@Repository
public class SentEmailRedisRepository implements SentEmailRepository {

  private static final String PREFIX = "idem:mail:";
  private static final String SENDING = "sending";
  private static final String SENT = "sent";
  private static final Duration CLAIM_LIFETIME = Duration.ofMinutes(1);
  private static final Duration SENT_LIFETIME = Duration.ofDays(7);

  private final StringRedisTemplate redis;

  public SentEmailRedisRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public SendClaim claim(String eventId) {
    String key = PREFIX + eventId;
    if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, SENDING, CLAIM_LIFETIME))) {
      return SendClaim.CLAIMED;
    }
    return SENT.equals(redis.opsForValue().get(key))
        ? SendClaim.ALREADY_SENT
        : SendClaim.IN_PROGRESS;
  }

  @Override
  public void markSent(String eventId) {
    redis.opsForValue().set(PREFIX + eventId, SENT, SENT_LIFETIME);
  }

  @Override
  public void release(String eventId) {
    redis.delete(PREFIX + eventId);
  }
}
