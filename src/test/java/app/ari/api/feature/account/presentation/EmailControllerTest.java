package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Adding, verifying, changing and removing the optional email. */
@IntegrationTest
class EmailControllerTest {

  /** One more than a rate-limit minute, so the next link is allowed. */
  private static final Duration NEXT_MINUTE = Duration.ofSeconds(61);

  @Autowired private MockMvcTester mvc;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;

  @Test
  void addingAnEmailKeepsItPendingAndQueuesItsLink() {
    Account account = signUp();
    String email = newEmail("bee");

    MvcTestResult result = changeEmail(account, email.toUpperCase(Locale.ROOT), "az");

    assertThat(result).hasStatus(HttpStatus.ACCEPTED);
    assertThat(result).bodyJson().extractingPath("$.pendingEmail").isEqualTo(email);
    assertThat(result).bodyJson().doesNotHavePath("$.email");
    Map<String, Object> event = lastEvent(account);
    assertThat(event)
        .containsEntry("aggregate_type", "USER")
        .containsEntry("event_type", "VerifyEmail")
        .containsEntry("routing_key", "email.transactional")
        .containsEntry("recipient", email)
        .containsEntry("full_name", "Test User")
        .containsEntry("language", "az");
    assertThat((String) event.get("link")).startsWith("http://localhost:3000/verify-email?code=");
  }

  @Test
  void theLinkConfirmsTheEmailOnceEvenWhenSignedOut() {
    Account account = signUp();
    String email = newEmail("bee");
    changeEmail(account, email, null);
    String code = lastCode(account);

    assertThat(verify(code)).hasStatus(HttpStatus.NO_CONTENT);

    MvcTestResult me = me(account);
    assertThat(me).bodyJson().extractingPath("$.email").isEqualTo(email);
    assertThat(me).bodyJson().doesNotHavePath("$.pendingEmail");
    assertThat(lastEvent(account)).containsEntry("language", "en");
    assertThat(verify(code))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CODE");
  }

  @Test
  void theLinkLastsADayAndRedisKeepsOnlyItsHash() {
    Account account = signUp();
    changeEmail(account, newEmail("bee"), null);

    String code = lastCode(account);

    assertThat(redis.hasKey("code:verify-email:" + code)).isFalse();
    assertThat(redis.getExpire("code:verify-email:" + sha256(code), TimeUnit.SECONDS))
        .isBetween(
            Duration.ofHours(24).minusMinutes(1).toSeconds(), Duration.ofHours(24).toSeconds());
  }

  @Test
  void aVerifiedEmailKeepsWorkingUntilTheNewOneIsConfirmed() {
    Account account = signUp();
    String oldEmail = newEmail("old");
    String newEmail = newEmail("new");
    verifiedEmail(account, oldEmail);
    clock.advance(NEXT_MINUTE);

    MvcTestResult changing = changeEmail(account, newEmail, null);

    assertThat(changing).hasStatus(HttpStatus.ACCEPTED);
    assertThat(changing).bodyJson().extractingPath("$.email").isEqualTo(oldEmail);
    assertThat(changing).bodyJson().extractingPath("$.pendingEmail").isEqualTo(newEmail);
    assertThat(verify(lastCode(account))).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(me(account)).bodyJson().extractingPath("$.email").isEqualTo(newEmail);
  }

  @Test
  void aLinkStopsWorkingWhenAnotherAddressIsAdded() {
    Account account = signUp();
    changeEmail(account, newEmail("first"), null);
    String first = lastCode(account);
    clock.advance(NEXT_MINUTE);
    changeEmail(account, newEmail("second"), null);

    assertThat(verify(first))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CODE");
    assertThat(verify(lastCode(account))).hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void anAddressAnotherAccountVerifiedIsOnlyReportedWhenItsLinkIsOpened() {
    Account owner = signUp();
    String shared = newEmail("shared");
    verifiedEmail(owner, shared);
    Account other = signUp();

    assertThat(changeEmail(other, shared, null)).hasStatus(HttpStatus.ACCEPTED);
    assertThat(verify(lastCode(other)))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("EMAIL_TAKEN");
    assertThat(me(owner)).bodyJson().extractingPath("$.email").isEqualTo(shared);
    assertThat(me(other)).bodyJson().doesNotHavePath("$.email");
  }

  @Test
  void addingTheVerifiedEmailAgainSendsNothing() {
    Account account = signUp();
    String email = newEmail("bee");
    verifiedEmail(account, email);
    clock.advance(NEXT_MINUTE);

    MvcTestResult result = changeEmail(account, email.toUpperCase(Locale.ROOT), null);

    assertThat(result).hasStatus(HttpStatus.OK);
    assertThat(result).bodyJson().doesNotHavePath("$.pendingEmail");
    assertThat(eventCount(account)).isOne();
  }

  @Test
  void linksAreLimitedToOneAMinuteAndFiveADay() {
    // Start at the beginning of a day, so the test never runs across midnight.
    Instant now = clock.instant();
    clock.advance(Duration.between(now, now.truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(1))));
    Account account = signUp();

    assertThat(changeEmail(account, newEmail("bee"), null)).hasStatus(HttpStatus.ACCEPTED);
    assertThat(resend(account))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("RATE_LIMITED");
    for (int link = 2; link <= 5; link++) {
      clock.advance(NEXT_MINUTE);
      assertThat(resend(account)).as("link %d", link).hasStatus(HttpStatus.ACCEPTED);
    }
    clock.advance(NEXT_MINUTE);

    assertThat(resend(account)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(eventCount(account)).isEqualTo(5);
  }

  @Test
  void resendNeedsAPendingEmail() {
    Account account = signUp();

    assertThat(resend(account))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("BAD_REQUEST");
  }

  @Test
  void removingClearsTheVerifiedAndThePendingEmail() {
    Account account = signUp();
    verifiedEmail(account, newEmail("old"));
    clock.advance(NEXT_MINUTE);
    changeEmail(account, newEmail("new"), null);

    assertThat(
            mvc.delete()
                .uri("/api/v1/me/email")
                .cookie(new Cookie(ACCESS, account.accessToken()))
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);

    MvcTestResult me = me(account);
    assertThat(me).bodyJson().doesNotHavePath("$.email");
    assertThat(me).bodyJson().doesNotHavePath("$.pendingEmail");
  }

  @Test
  void theEmailMustLookLikeOne() {
    assertThat(changeEmail(signUp(), "not-an-email", null))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void verifyNeedsAKnownCode() {
    assertThat(verify("")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(verify("unknown-code"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CODE");
  }

  @Test
  void changingTheEmailNeedsASignedInUser() {
    assertThat(
            mvc.put()
                .uri("/api/v1/me/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"bee@example.com\"}")
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.post().uri("/api/v1/me/email/resend").exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.delete().uri("/api/v1/me/email").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  /** A new user and the access token from signing up. */
  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "mail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"username": "%s", "password": "correct horse", "fullName": "Test User",
                 "birthDate": "1990-05-17"}
                """
                    .formatted(username))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    Cookie access = result.getResponse().getCookie(ACCESS);
    assertThat(access).isNotNull();
    UUID id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    return new Account(id, access.getValue());
  }

  private MvcTestResult changeEmail(Account account, String email, String language) {
    var request =
        mvc.put()
            .uri("/api/v1/me/email")
            .cookie(new Cookie(ACCESS, account.accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\"}".formatted(email));
    if (language != null) {
      request = request.cookie(new Cookie(AccountController.LANGUAGE_COOKIE, language));
    }
    return request.exchange();
  }

  private MvcTestResult resend(Account account) {
    return mvc.post()
        .uri("/api/v1/me/email/resend")
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult verify(String code) {
    return mvc.post()
        .uri("/api/v1/auth/verify-email")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\": \"%s\"}".formatted(code))
        .exchange();
  }

  private MvcTestResult me(Account account) {
    return mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, account.accessToken())).exchange();
  }

  private void verifiedEmail(Account account, String email) {
    assertThat(changeEmail(account, email, null)).hasStatus(HttpStatus.ACCEPTED);
    assertThat(verify(lastCode(account))).hasStatus(HttpStatus.NO_CONTENT);
  }

  /** The newest outbox row for this user, with its payload's fields as columns. */
  private Map<String, Object> lastEvent(Account account) {
    return jdbc.queryForMap(
        "SELECT aggregate_type, event_type, routing_key, payload->>'to' AS recipient,"
            + " payload->>'fullName' AS full_name, payload->>'language' AS language,"
            + " payload->>'link' AS link"
            + " FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at DESC LIMIT 1",
        account.id());
  }

  private String lastCode(Account account) {
    String link = (String) lastEvent(account).get("link");
    return link.substring(link.indexOf("code=") + "code=".length());
  }

  private int eventCount(Account account) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
            Integer.class,
            account.id());
    return count == null ? 0 : count;
  }

  /** Tests share one database and verified emails are unique, so each test makes its own. */
  private static String newEmail(String name) {
    return name + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
