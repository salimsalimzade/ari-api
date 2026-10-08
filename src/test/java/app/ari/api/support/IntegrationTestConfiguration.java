package app.ari.api.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real PostgreSQL (Docker must be running) and a clock the tests control. */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

  @Bean
  @ServiceConnection
  public PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18");
  }

  /** Starts at a fixed time; tests move it forward to expire tokens. */
  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  }
}
