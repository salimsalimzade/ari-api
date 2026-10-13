package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.common.ratelimit.RateLimiter;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.port.UsernameFilter;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.UsernameService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class UsernameServiceImpl implements UsernameService {

  private static final int PAGE_SIZE = 1000;

  private final UserRepository users;
  private final UsernameFilter filter;
  private final RateLimiter rateLimiter;
  private final Clock clock;

  public UsernameServiceImpl(
      UserRepository users, UsernameFilter filter, RateLimiter rateLimiter, Clock clock) {
    this.users = users;
    this.filter = filter;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  @Override
  public boolean mayCheck(String clientAddress) {
    return rateLimiter.tryAcquire("username-check", clientAddress, 30, Duration.ofMinutes(1));
  }

  /** "Definitely free" needs no query; "maybe taken", or no answer, asks the database. */
  @Override
  public boolean isAvailable(String username) {
    String normalized = Usernames.normalize(username);
    return !filter.mightContain(normalized).orElse(true) || !users.usernameExists(normalized);
  }

  @Override
  public void remember(String username) {
    filter.add(Usernames.normalize(username));
  }

  @Override
  public int fillFilterIfMissing() {
    return filter.isPresent() ? -1 : rebuildFilter();
  }

  @Override
  public int rebuildFilter() {
    Instant started = clock.instant();
    int count = filter.rebuild(pages());
    // Usernames taken or changed while the new filter was being built.
    filter.addAll(users.findUsernamesUpdatedSince(started));
    return count;
  }

  /** Every username, a page at a time, in order. */
  private Stream<List<String>> pages() {
    return Stream.iterate(
        users.findUsernamesAfter("", PAGE_SIZE),
        page -> !page.isEmpty(),
        page ->
            page.size() < PAGE_SIZE
                ? List.of()
                : users.findUsernamesAfter(page.getLast(), PAGE_SIZE));
  }
}
