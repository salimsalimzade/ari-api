package app.ari.api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A UTC clock that only moves when a test calls {@link #advance}. */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;

  public MutableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  public void advance(Duration duration) {
    now.updateAndGet(instant -> instant.plus(duration));
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("MutableClock is always UTC");
  }
}
