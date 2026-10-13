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
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = 'public'"
                    + " AND table_name IN ('users', 'refresh_tokens', 'outbox_events')"
                    + " ORDER BY table_name",
                String.class))
        .containsExactly("outbox_events", "refresh_tokens", "users");
  }

  @ParameterizedTest
  @ValueSource(strings = {"email", "pending_email"})
  void emailsMustBeStoredLowercase(String column) {
    String username = "case_" + UUID.randomUUID().toString().substring(0, 8);
    String insert =
        "INSERT INTO users (id, username, full_name, %s) VALUES (?, ?, 'Case Test', ?)"
            .formatted(column);

    assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), username, "Bee@Example.com"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.update(insert, UUID.randomUUID(), username, "bee@example.com")).isOne();
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
