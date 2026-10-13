package app.ari.api.feature.account.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ari.api.common.ratelimit.RateLimiter;
import app.ari.api.feature.account.domain.port.UsernameFilter;
import app.ari.api.feature.account.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** When the Bloom filter is enough and when the database decides. */
class UsernameServiceImplTest {

  private final UserRepository users = mock(UserRepository.class);
  private final FixedFilter filter = new FixedFilter();
  private final UsernameServiceImpl usernames =
      new UsernameServiceImpl(users, filter, mock(RateLimiter.class), Clock.systemUTC());

  @Test
  void definitelyFreeNeedsNoQuery() {
    filter.answer = Optional.of(false);

    assertThat(usernames.isAvailable("Bee.Reader")).isTrue();
    verify(users, never()).usernameExists(anyString());
  }

  @Test
  void maybeTakenAsksTheDatabase() {
    filter.answer = Optional.of(true);
    when(users.usernameExists("bee.reader")).thenReturn(true);
    when(users.usernameExists("old.name")).thenReturn(false);

    assertThat(usernames.isAvailable("Bee.Reader")).isFalse();
    // A false positive, or a name somebody gave up: the database says it is free.
    assertThat(usernames.isAvailable("old.name")).isTrue();
  }

  @Test
  void withoutAnAnswerTheDatabaseDecides() {
    filter.answer = Optional.empty();
    when(users.usernameExists("bee.reader")).thenReturn(true);

    assertThat(usernames.isAvailable("bee.reader")).isFalse();
  }

  @Test
  void aPresentFilterIsNotFilledAgain() {
    filter.present = true;

    assertThat(usernames.fillFilterIfMissing()).isEqualTo(-1);
  }

  /** Answers whatever the test sets. */
  private static final class FixedFilter implements UsernameFilter {

    Optional<Boolean> answer = Optional.empty();
    boolean present;

    @Override
    public Optional<Boolean> mightContain(String username) {
      return answer;
    }

    @Override
    public void add(String username) {}

    @Override
    public void addAll(Collection<String> usernames) {}

    @Override
    public boolean isPresent() {
      return present;
    }

    @Override
    public int rebuild(Stream<List<String>> pages) {
      return 0;
    }
  }
}
