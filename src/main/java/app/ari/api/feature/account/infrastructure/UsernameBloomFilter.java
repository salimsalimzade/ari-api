package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.port.UsernameFilter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for {@link UsernameFilter}: Redis 8's Bloom filter under the key {@code usernames},
 * 0.1% false positives, room for a million names before it grows. The {@code BF.*} commands run in
 * small Lua scripts, which also refuse to read or add to a missing filter.
 */
@Component
public class UsernameBloomFilter implements UsernameFilter {

  static final String KEY = "usernames";

  private static final Logger log = LoggerFactory.getLogger(UsernameBloomFilter.class);

  /** 1 or 0, or -1 while the filter is missing. */
  private static final RedisScript<Long> EXISTS =
      script(
          """
          if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
          return redis.call('BF.EXISTS', KEYS[1], ARGV[1])
          """);

  /** How many names were sent, or -1 while the filter is missing. */
  private static final RedisScript<Long> ADD_ALL =
      script(
          """
          if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
          return #redis.call('BF.MADD', KEYS[1], unpack(ARGV))
          """);

  private static final RedisScript<Long> RESERVE =
      script(
          """
          redis.call('BF.RESERVE', KEYS[1], '0.001', '1000000', 'EXPANSION', '2')
          return 1
          """);

  private final StringRedisTemplate redis;

  public UsernameBloomFilter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Optional<Boolean> mightContain(String username) {
    try {
      Long answer = redis.execute(EXISTS, List.of(KEY), username);
      return answer == null || answer < 0 ? Optional.empty() : Optional.of(answer == 1);
    } catch (DataAccessException e) {
      log.warn("Usernames: the Bloom filter can't answer, asking the database: {}", e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void add(String username) {
    addAll(List.of(username));
  }

  @Override
  public void addAll(Collection<String> usernames) {
    if (usernames.isEmpty()) {
      return;
    }
    try {
      redis.execute(ADD_ALL, List.of(KEY), usernames.toArray());
    } catch (DataAccessException e) {
      log.warn("Usernames: could not add to the Bloom filter: {}", e.getMessage());
    }
  }

  @Override
  public boolean isPresent() {
    return Boolean.TRUE.equals(redis.hasKey(KEY));
  }

  /** Fills a new key and renames it over the old one, so checks never see a half-built filter. */
  @Override
  public int rebuild(Stream<List<String>> pages) {
    String building = KEY + ":building:" + UUID.randomUUID();
    try {
      redis.execute(RESERVE, List.of(building));
      int count =
          pages
              .mapToInt(
                  page -> {
                    redis.execute(ADD_ALL, List.of(building), page.toArray());
                    return page.size();
                  })
              .sum();
      redis.rename(building, KEY);
      return count;
    } catch (RuntimeException e) {
      redis.delete(building);
      throw e;
    }
  }

  private static RedisScript<Long> script(String lua) {
    return new DefaultRedisScript<>(lua, Long.class);
  }
}
