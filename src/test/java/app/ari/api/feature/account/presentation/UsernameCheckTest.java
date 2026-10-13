package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.domain.port.UsernameFilter;
import app.ari.api.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The live username check, with the Bloom filter in a real Redis. */
@IntegrationTest
class UsernameCheckTest {

  @Autowired private MockMvcTester mvc;
  @Autowired private UsernameFilter filter;
  @Autowired private AccountFacade accounts;
  @Autowired private StringRedisTemplate redis;

  @Test
  void aUsernameIsFreeUntilSomebodyRegistersItInAnyCase() {
    String username = newUsername();
    String ip = newIp();

    assertThat(check(username.toUpperCase(Locale.ROOT), ip))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.available")
        .isEqualTo(true);
    signUp(username);

    MvcTestResult taken = check(username.toUpperCase(Locale.ROOT), ip);
    assertThat(taken).bodyJson().extractingPath("$.available").isEqualTo(false);
    assertThat(taken).bodyJson().extractingPath("$.username").isEqualTo(username);
  }

  @Test
  void registeringPutsTheUsernameInTheBloomFilter() {
    String username = newUsername();

    signUp(username);

    assertThat(filter.mightContain(username)).contains(true);
    // At 0.1% false positives, five fresh names are never all "maybe taken".
    assertThat(IntStream.range(0, 5).mapToObj(i -> filter.mightContain(newUsername())))
        .contains(Optional.of(false));
  }

  @Test
  void aRenamedAccountFreesItsOldUsername() {
    String before = newUsername();
    String after = newUsername();
    String accessToken = signUp(before);
    String ip = newIp();

    rename(accessToken, after);

    assertThat(filter.mightContain(after)).contains(true);
    assertThat(check(after, ip)).bodyJson().extractingPath("$.available").isEqualTo(false);
    // Still in the filter, which can't forget, but the database says it is free.
    assertThat(check(before, ip)).bodyJson().extractingPath("$.available").isEqualTo(true);
  }

  @Test
  void withoutTheFilterTheDatabaseAnswersUntilItIsFilledAgain() {
    String username = newUsername();
    signUp(username);
    redis.delete("usernames");

    assertThat(filter.mightContain(username)).isEmpty();
    filter.add(username);
    assertThat(filter.isPresent()).as("an add never starts an empty filter").isFalse();
    assertThat(check(username, newIp())).bodyJson().extractingPath("$.available").isEqualTo(false);

    assertThat(accounts.fillUsernameFilterIfMissing()).isPositive();
    assertThat(filter.mightContain(username)).contains(true);
    assertThat(accounts.fillUsernameFilterIfMissing()).isEqualTo(-1);
  }

  @Test
  void theNightlyRebuildDropsUsernamesNobodyHas() {
    List<String> before = List.of(newUsername(), newUsername(), newUsername());
    List<String> after = List.of(newUsername(), newUsername(), newUsername());
    for (int i = 0; i < before.size(); i++) {
      rename(signUp(before.get(i)), after.get(i));
    }

    accounts.rebuildUsernameFilter();

    assertThat(after).allSatisfy(name -> assertThat(filter.mightContain(name)).contains(true));
    // At 0.1% false positives, three dropped names never all stay "maybe taken".
    assertThat(before.stream().map(filter::mightContain)).contains(Optional.of(false));
  }

  @Test
  void onlyValidUsernamesAreChecked() {
    assertThat(check("a b", newIp()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
    assertThat(check("ab", newIp())).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anAddressGetsThirtyChecksAMinute() {
    String ip = newIp();
    for (int i = 0; i < 30; i++) {
      assertThat(check(newUsername(), ip)).as("check %d", i + 1).hasStatusOk();
    }

    assertThat(check(newUsername(), ip))
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("RATE_LIMITED");
    assertThat(check(newUsername(), newIp())).hasStatusOk();
  }

  private MvcTestResult check(String username, String ip) {
    return mvc.get()
        .uri("/api/v1/auth/username-available")
        .param("username", username)
        .with(
            request -> {
              request.setRemoteAddr(ip);
              return request;
            })
        .exchange();
  }

  private String signUp(String username) {
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
    return access.getValue();
  }

  private void rename(String accessToken, String username) {
    assertThat(
            mvc.put()
                .uri("/api/v1/me")
                .cookie(new Cookie(ACCESS, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username": "%s", "fullName": "Test User", "birthDate": "1990-05-17"}
                    """
                        .formatted(username))
                .exchange())
        .hasStatusOk();
  }

  private static String newUsername() {
    return "name_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  }

  /** Each test checks from its own address, so the rate limit of one never touches another. */
  private static String newIp() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
  }
}
