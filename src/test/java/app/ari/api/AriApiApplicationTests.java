package app.ari.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ari.api.support.IntegrationTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The app as it starts in CI and on a fresh clone. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfiguration.class)
class AriApiApplicationTests {

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void liquibaseCreatesTheTables() {
    // Sessions live in Supabase Auth now: no refresh_tokens table.
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = 'public'"
                    + " AND table_name IN ('users', 'refresh_tokens', 'outbox_events')"
                    + " ORDER BY table_name",
                String.class))
        .containsExactly("outbox_events", "users");
  }

  @ParameterizedTest
  @ValueSource(strings = {"email", "pending_email"})
  void emailsMustBeStoredLowercase(String column) {
    UUID id = newProfile();
    String update = "UPDATE users SET %s = ? WHERE id = ?".formatted(column);

    assertThatThrownBy(() -> jdbc.update(update, "Bee@Example.com", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.update(update, "bee@example.com", id)).isOne();
  }

  @Test
  void aProfileNeedsASupabaseUserAndGoesWithIt() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (id, username, full_name) VALUES (?, ?, 'No Auth')",
                    UUID.randomUUID(),
                    "noauth_" + UUID.randomUUID().toString().substring(0, 8)))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID id = newProfile();
    jdbc.update("DELETE FROM auth.users WHERE id = ?", id);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id))
        .isZero();
  }

  @Test
  void supabasesDataApiCannotReadTheTables() {
    // Every table has row-level security on, and the API roles have no grants on any of them.
    assertThat(
            jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity",
                String.class))
        .isEmpty();
    for (String role : new String[] {"anon", "authenticated", "service_role"}) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.role_table_grants"
                      + " WHERE table_schema = 'public' AND grantee = ?",
                  Integer.class,
                  role))
          .as(role)
          .isZero();
    }
  }

  /** A Supabase user with its profile, as the trigger makes it. */
  private UUID newProfile() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO auth.users (id, aud, role, email, raw_user_meta_data)"
            + " VALUES (?, 'authenticated', 'authenticated', ?, jsonb_build_object('username', ?))",
        id,
        id + "@no-email.ari.invalid",
        "case_" + id.toString().substring(0, 8));
    return id;
  }

  @Test
  void healthIsPublicAndUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }
}
