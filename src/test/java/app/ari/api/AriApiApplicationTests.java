package app.ari.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
  void liquibaseCreatesTheSignInTables() {
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = 'public'"
                    + " AND table_name IN ('users', 'oauth_accounts', 'refresh_tokens')"
                    + " ORDER BY table_name",
                String.class))
        .containsExactly("refresh_tokens", "users");
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
