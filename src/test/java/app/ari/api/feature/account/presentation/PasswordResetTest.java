package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.REFRESH;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A forgotten password: the link to the verified email, then a new password. */
@IntegrationTest
class PasswordResetTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvcTester mvc;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;

  // — Asking for a link —

  @Test
  void queuesALinkToTheVerifiedEmailByUsernameOrEmail() {
    Account account = signUp();
    String email = verifiedEmail(account);

    MvcTestResult byUsername = request(account.username().toUpperCase(Locale.ROOT), "az");

    assertThat(byUsername).hasStatus(HttpStatus.NO_CONTENT);
    Map<String, Object> event = lastEvent(account);
    assertThat(event)
        .containsEntry("event_type", "ResetPassword")
        .containsEntry("routing_key", "email.transactional")
        .containsEntry("recipient", email)
        .containsEntry("username", account.username())
        .containsEntry("language", "az");
    assertThat((String) event.get("link")).startsWith("http://localhost:3000/reset-password?code=");

    clock.advance(Duration.ofSeconds(61));
    assertThat(request("  " + email.toUpperCase(Locale.ROOT) + " ", null))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(lastEvent(account)).containsEntry("language", "en");
  }

  @Test
  void saysWhyNoLinkWasSent() {
    Account noEmail = signUp();
    Account pending = signUp();
    String pendingEmail = newEmail();
    jdbc.update("UPDATE users SET pending_email = ? WHERE id = ?", pendingEmail, pending.id());

    assertCode(
        request("nobody_" + UUID.randomUUID().toString().substring(0, 8), null),
        404,
        "ACCOUNT_NOT_FOUND");
    assertCode(request(newEmail(), null), 404, "ACCOUNT_NOT_FOUND");
    assertCode(request(noEmail.username(), null), 409, "NO_EMAIL");
    assertCode(request(pending.username(), null), 409, "EMAIL_NOT_CONFIRMED");
    assertCode(request(pendingEmail, null), 409, "EMAIL_NOT_CONFIRMED");
    assertCode(request("  ", null), 400, "VALIDATION_FAILED");
  }

  @Test
  void anAddressMayAskTwentyTimesAnHour() {
    String address = newAddress();
    for (int i = 0; i < 20; i++) {
      assertThat(request(newEmail(), null, address)).hasStatus(HttpStatus.NOT_FOUND);
    }

    assertCode(request(newEmail(), null, address), 429, "RATE_LIMITED");
    assertThat(request(newEmail(), null)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void anAccountGetsOneEmailAMinute() {
    Account account = signUp();
    verifiedEmail(account);
    assertThat(request(account.username(), null)).hasStatus(HttpStatus.NO_CONTENT);

    assertCode(request(account.username(), null), 429, "RATE_LIMITED");

    clock.advance(Duration.ofSeconds(61));
    assertThat(request(account.username(), null)).hasStatus(HttpStatus.NO_CONTENT);
  }

  // — Using it —

  @Test
  void theLinkSetsANewPasswordOnceAndEndsEverySession() {
    Account account = signUp();
    verifiedEmail(account);
    request(account.username(), null);
    String code = lastCode(account);

    MvcTestResult checked = check(code);
    assertThat(checked).hasStatusOk();
    assertThat(checked).bodyJson().extractingPath("$.username").isEqualTo(account.username());
    // Checking doesn't use it up.
    assertThat(check(code)).hasStatusOk();

    assertThat(confirm(code, "a new password")).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(login(account.username(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(login(account.username(), "a new password")).hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH, account.refreshToken()))
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertCode(confirm(code, "yet another one"), 410, "CODE_USED");
    assertCode(check(code), 410, "CODE_USED");
    assertThat(login(account.username(), "a new password")).hasStatusOk();
  }

  @Test
  void aLinkWorksForThirtyMinutes() {
    Account account = signUp();
    verifiedEmail(account);
    request(account.username(), null);
    String code = lastCode(account);

    clock.advance(Duration.ofMinutes(29));
    assertThat(check(code)).hasStatusOk();
    clock.advance(Duration.ofMinutes(2));

    assertCode(check(code), 410, "CODE_EXPIRED");
    assertCode(confirm(code, "a new password"), 410, "CODE_EXPIRED");
    assertThat(login(account.username(), PASSWORD)).hasStatusOk();
  }

  @Test
  void anUnknownLinkDoesntWorkAndRedisKeepsOnlyTheHash() {
    Account account = signUp();
    verifiedEmail(account);
    request(account.username(), null);
    String code = lastCode(account);

    assertCode(check("not-a-code"), 401, "INVALID_CODE");
    assertCode(confirm("not-a-code", "a new password"), 401, "INVALID_CODE");
    assertCode(check(" "), 400, "VALIDATION_FAILED");
    assertThat(redis.hasKey("code:password-reset:" + code)).isFalse();
    assertThat(redis.getExpire("code:password-reset:" + sha256(code), TimeUnit.SECONDS))
        .isBetween(Duration.ofDays(7).minusMinutes(1).toSeconds(), Duration.ofDays(7).toSeconds());
  }

  @Test
  void theNewPasswordFollowsTheSignUpRulesAndAFailureKeepsTheLink() {
    Account account = signUp();
    verifiedEmail(account);
    request(account.username(), null);
    String code = lastCode(account);

    assertCode(confirm(code, "short"), 400, "VALIDATION_FAILED");

    assertThat(check(code)).hasStatusOk();
    assertThat(confirm(code, "long enough now")).hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void theOpenApiDescriptionShowsTheResetEndpoints() {
    MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(docs)
        .bodyJson()
        .hasPathSatisfying(
            "$.paths['/api/v1/auth/password-reset'].post", value -> assertThat(value).isNotNull());
    assertThat(docs)
        .bodyJson()
        .hasPathSatisfying(
            "$.paths['/api/v1/auth/password-reset/check'].post",
            value -> assertThat(value).isNotNull());
    assertThat(docs)
        .bodyJson()
        .hasPathSatisfying(
            "$.paths['/api/v1/auth/password-reset/confirm'].post",
            value -> assertThat(value).isNotNull());
  }

  // — Helpers —

  private record Account(UUID id, String username, String refreshToken) {}

  private Account signUp() {
    String username = "reset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"username": "%s", "password": "%s", "fullName": "Test User",
                 "birthDate": "1990-05-17"}
                """
                    .formatted(username, PASSWORD))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    Cookie refresh = result.getResponse().getCookie(REFRESH);
    assertThat(refresh).isNotNull();
    UUID id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    return new Account(id, username, refresh.getValue());
  }

  /** Gives the account a verified email, as a confirmed verify link would. */
  private String verifiedEmail(Account account) {
    String email = newEmail();
    jdbc.update(
        "UPDATE users SET email = ?, email_verified_at = now() WHERE id = ?", email, account.id());
    return email;
  }

  /** Asks for a link from an address of the test's own, so the address limit stays out of it. */
  private MvcTestResult request(String login, String language) {
    return request(login, language, newAddress());
  }

  private MvcTestResult request(String login, String language, String address) {
    var request =
        mvc.post()
            .uri("/api/v1/auth/password-reset")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\": \"%s\"}".formatted(login))
            .with(
                servletRequest -> {
                  servletRequest.setRemoteAddr(address);
                  return servletRequest;
                });
    if (language != null) {
      request = request.cookie(new Cookie(AccountController.LANGUAGE_COOKIE, language));
    }
    return request.exchange();
  }

  private MvcTestResult check(String code) {
    return mvc.post()
        .uri("/api/v1/auth/password-reset/check")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\": \"%s\"}".formatted(code))
        .exchange();
  }

  private MvcTestResult confirm(String code, String password) {
    return mvc.post()
        .uri("/api/v1/auth/password-reset/confirm")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\": \"%s\", \"password\": \"%s\"}".formatted(code, password))
        .exchange();
  }

  private MvcTestResult login(String username, String password) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password))
        .exchange();
  }

  private static void assertCode(MvcTestResult result, int status, String code) {
    assertThat(result).hasStatus(status);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
  }

  /** The newest outbox row for this user, with its payload's fields as columns. */
  private Map<String, Object> lastEvent(Account account) {
    return jdbc.queryForMap(
        "SELECT event_type, routing_key, payload->>'to' AS recipient,"
            + " payload->>'username' AS username, payload->>'language' AS language,"
            + " payload->>'link' AS link"
            + " FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at DESC LIMIT 1",
        account.id());
  }

  private String lastCode(Account account) {
    String link = (String) lastEvent(account).get("link");
    return link.substring(link.indexOf("code=") + "code=".length());
  }

  private static String newEmail() {
    return "reset." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }

  private static String newAddress() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
