package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static app.ari.api.feature.account.presentation.cookie.AuthCookies.REFRESH;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AccountControllerTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvcTester mvc;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void registerCreatesTheAccountAndSignsItIn() {
    String email = newEmail();

    MvcTestResult result = register(email.toUpperCase(Locale.ROOT), PASSWORD);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email);
    assertThat(result).bodyJson().extractingPath("$.plan").isEqualTo("FREE");
    assertThat(result).bodyJson().extractingPath("$.savesLimit").isEqualTo(500);
    assertThat(result)
        .cookies()
        .isHttpOnly(ACCESS, true)
        .isHttpOnly(REFRESH, true)
        .hasPath(ACCESS, "/")
        .hasPath(REFRESH, "/")
        .hasMaxAge(ACCESS, Duration.ofMinutes(14))
        .hasMaxAge(REFRESH, Duration.ofDays(30));
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .allSatisfy(cookie -> assertThat(cookie).contains("SameSite=Lax"));
  }

  @Test
  void registerRejectsATakenEmailInAnyCase() {
    String email = newEmail();
    register(email, PASSWORD);

    MvcTestResult result = register(email.toUpperCase(Locale.ROOT), PASSWORD);

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void registerValidatesTheBody() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "not-an-email", "username": "a b", "password": "short",
                 "fullName": "", "birthDate": "2999-01-01"}
                """)
            .exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("email", "username", "password", "fullName", "birthDate");
  }

  @Test
  void registerRequiresABirthDate() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "username": "%s", "password": "%s", "fullName": "Test User"}
                """
                    .formatted(newEmail(), newUsername(), PASSWORD))
            .exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("birthDate");
  }

  @Test
  void registerStoresTheUsernameInLowercaseAndReturnsTheProfile() {
    String username = "Ada.Lovelace" + UUID.randomUUID().toString().substring(0, 4);

    MvcTestResult result = register(newEmail(), username, PASSWORD);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.username")
        .isEqualTo(username.toLowerCase(Locale.ROOT));
    assertThat(result).bodyJson().extractingPath("$.fullName").isEqualTo("Test User");
    assertThat(result).bodyJson().extractingPath("$.birthDate").isEqualTo("1990-05-17");
  }

  @Test
  void registerRejectsATakenUsernameInAnyCase() {
    String username = newUsername();
    register(newEmail(), username, PASSWORD);

    MvcTestResult result = register(newEmail(), username.toUpperCase(Locale.ROOT), PASSWORD);

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("USERNAME_TAKEN");
  }

  @Test
  void registerRejectsPasswordsLongerThanBcryptAccepts() {
    // 25 characters but 75 bytes: bcrypt only takes 72.
    MvcTestResult result = register(newEmail(), "€".repeat(25));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
  }

  @Test
  void loginWorksForTheDemoUser() {
    MvcTestResult result = login("demo@example.com", "demo1234");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.fullName").isEqualTo("Demo User");
    assertThat(result).bodyJson().extractingPath("$.username").isEqualTo("demo");
    assertThat(result).cookies().containsCookies(ACCESS, REFRESH);
  }

  @Test
  void loginAnswersTheSameForWrongPasswordAndUnknownEmail() {
    String email = newEmail();
    register(email, PASSWORD);

    for (MvcTestResult result :
        new MvcTestResult[] {login(email, "wrong password"), login(newEmail(), PASSWORD)}) {
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
      assertThat(result).cookies().doesNotContainCookie(ACCESS);
    }
  }

  @Test
  void meReturnsTheSignedInUserFromTheCookieOrTheBearerHeader() {
    String email = newEmail();
    String accessToken = cookie(register(email, PASSWORD), ACCESS);

    assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, accessToken)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.email")
        .isEqualTo(email);
    assertThat(
            mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.email")
        .isEqualTo(email);
  }

  @Test
  void meNeedsAValidToken() {
    for (MvcTestResult result :
        new MvcTestResult[] {
          mvc.get().uri("/api/v1/me").exchange(),
          mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, "not-a-jwt")).exchange()
        }) {
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }
  }

  @Test
  void accessTokensExpire() {
    String accessToken = cookie(register(newEmail(), PASSWORD), ACCESS);

    clock.advance(Duration.ofMinutes(17)); // 15 minutes plus the decoder's 1 minute of clock skew

    assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, accessToken)))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void meClearsTheCookiesWhenTheUserIsGone() {
    String email = newEmail();
    String accessToken = cookie(register(email, PASSWORD), ACCESS);
    jdbc.update("DELETE FROM users WHERE email = ?", email);

    MvcTestResult result =
        mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, accessToken)).exchange();

    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(result).cookies().hasMaxAge(ACCESS, Duration.ZERO).hasMaxAge(REFRESH, Duration.ZERO);
  }

  @Test
  void refreshReplacesBothTokens() {
    MvcTestResult signedIn = register(newEmail(), PASSWORD);
    String oldRefresh = cookie(signedIn, REFRESH);

    MvcTestResult result = refresh(oldRefresh);

    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(cookie(result, REFRESH)).isNotEqualTo(oldRefresh);
    assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, cookie(result, ACCESS))))
        .hasStatusOk();
  }

  @Test
  void aReplacedRefreshTokenWorksForTenSecondsThenEndsTheSession() {
    String first = cookie(register(newEmail(), PASSWORD), REFRESH);
    String second = cookie(refresh(first), REFRESH);

    clock.advance(Duration.ofSeconds(5));
    MvcTestResult withinGrace = refresh(first);
    assertThat(withinGrace).hasStatus(HttpStatus.NO_CONTENT);
    String third = cookie(withinGrace, REFRESH);

    clock.advance(Duration.ofSeconds(6));
    MvcTestResult reused = refresh(first);
    assertThat(reused).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(reused).cookies().hasMaxAge(ACCESS, Duration.ZERO).hasMaxAge(REFRESH, Duration.ZERO);

    // Reuse revoked the whole session, including the tokens issued after the first one.
    assertThat(refresh(second)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(third)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshNeedsAKnownToken() {
    assertThat(mvc.post().uri("/api/v1/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(refresh("unknown-token"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  void logoutEndsTheSessionAndClearsTheCookies() {
    String refreshToken = cookie(register(newEmail(), PASSWORD), REFRESH);

    MvcTestResult result =
        mvc.post().uri("/api/v1/auth/logout").cookie(new Cookie(REFRESH, refreshToken)).exchange();

    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(result).cookies().hasMaxAge(ACCESS, Duration.ZERO).hasMaxAge(REFRESH, Duration.ZERO);
    assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aStaleAccessCookieNeverBlocksSigningIn() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/login")
            .cookie(new Cookie(ACCESS, "expired-or-garbage"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "demo@example.com", "password": "demo1234"}
                """)
            .exchange();

    assertThat(result).hasStatusOk();
  }

  @Test
  void theWebsiteMayCallTheApiWithCookies() {
    assertThat(
            mvc.options()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
        .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
  }

  private MvcTestResult register(String email, String password) {
    return register(email, newUsername(), password);
  }

  private MvcTestResult register(String email, String username, String password) {
    return mvc.post()
        .uri("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "%s", "username": "%s", "password": "%s", "fullName": "Test User",
             "birthDate": "1990-05-17"}
            """
                .formatted(email, username, password))
        .exchange();
  }

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "%s", "password": "%s"}
            """
                .formatted(email, password))
        .exchange();
  }

  private MvcTestResult refresh(String refreshToken) {
    return mvc.post()
        .uri("/api/v1/auth/refresh")
        .cookie(new Cookie(REFRESH, refreshToken))
        .exchange();
  }

  private static String cookie(MvcTestResult result, String name) {
    Cookie cookie = result.getResponse().getCookie(name);
    assertThat(cookie).as("cookie %s", name).isNotNull();
    return cookie.getValue();
  }

  private static String newUsername() {
    return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  }

  private static String newEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }
}
