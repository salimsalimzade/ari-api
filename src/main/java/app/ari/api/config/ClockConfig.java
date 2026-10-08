package app.ari.api.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  /**
   * UTC clock with microsecond ticks, the precision of PostgreSQL's {@code timestamptz}, so a time
   * reads back from the database exactly as it was written.
   */
  @Bean
  public Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
