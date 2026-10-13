package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.model.Plan;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adds the demo user {@code demo} / {@code demo1234} at startup when {@code app.seed-demo-data} is
 * on (local development and tests), so production never has a known password. The id is fixed so
 * later seed data can belong to this user.
 */
@Component
@ConditionalOnBooleanProperty("app.seed-demo-data")
public class DemoUserSeeder implements ApplicationRunner {

  static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final String DEMO_USERNAME = "demo";
  static final String DEMO_PASSWORD = "demo1234";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (users.findByUsername(DEMO_USERNAME).isPresent()) {
      return;
    }
    Instant now = clock.instant();
    users.insert(
        new User(
            DEMO_USER_ID,
            null,
            null,
            DEMO_USERNAME,
            passwordEncoder.encode(DEMO_PASSWORD),
            "Demo User",
            LocalDate.of(1990, 1, 1),
            null,
            Plan.NONE,
            0,
            now,
            now,
            now));
  }
}
