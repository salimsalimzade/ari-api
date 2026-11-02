package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.model.NoEmail;
import app.ari.api.feature.account.domain.port.IdentityProvider;
import app.ari.api.feature.account.domain.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Adds the demo user {@code demo} / {@code demo1234} at startup when {@code app.seed-demo-data} is
 * on (local development and tests), so production never has a known password. It is a Supabase user
 * like any other, so Supabase Auth must be running; without it the API starts all the same. The id
 * is fixed so later seed data can belong to this user.
 */
@Component
@ConditionalOnBooleanProperty("app.seed-demo-data")
public class DemoUserSeeder implements ApplicationRunner {

  static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final String DEMO_USERNAME = "demo";
  static final String DEMO_PASSWORD = "demo1234";

  private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

  private final UserRepository users;
  private final IdentityProvider identity;

  public DemoUserSeeder(UserRepository users, IdentityProvider identity) {
    this.users = users;
    this.identity = identity;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (users.findByUsername(DEMO_USERNAME).isPresent()) {
      return;
    }
    try {
      identity.createUser(
          DEMO_USER_ID,
          NoEmail.of(DEMO_USER_ID),
          DEMO_PASSWORD,
          Map.of("username", DEMO_USERNAME, "full_name", "Demo User", "birth_date", "1990-01-01"));
    } catch (RuntimeException e) {
      log.warn("No demo user: Supabase Auth could not create it ({})", e.getMessage());
    }
  }
}
