package app.ari.api.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limits in Redis: one counter per action, subject and window, which expires with
 * its window. Keys look like {@code rate:email-send:{userId}:{window number}}.
 */
@Component
public class RateLimiter {

  private final StringRedisTemplate redis;
  private final Clock clock;

  public RateLimiter(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  /**
   * Counts one more attempt and tells whether it is still within {@code limit} per {@code window}.
   * Every call counts, including the ones it turns down.
   */
  public boolean tryAcquire(String action, String subject, int limit, Duration window) {
    long windowNumber = clock.millis() / window.toMillis();
    String key = "rate:" + action + ":" + subject + ":" + windowNumber;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1) {
      redis.expire(key, window);
    }
    return count != null && count <= limit;
  }
}
