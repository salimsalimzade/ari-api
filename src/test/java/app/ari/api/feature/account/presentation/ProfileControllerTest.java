package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Changing the full name, username and date of birth. */
@IntegrationTest
class ProfileControllerTest {

  @Autowired private MockMvcTester mvc;

  @Test
  void updatesTheProfileAndTheNewUsernameSignsIn() {
    String accessToken = signUp(newUsername());
    String renamed = newUsername();

    MvcTestResult result =
        updateProfile(
            accessToken, renamed.toUpperCase(Locale.ROOT), "  Ada Lovelace  ", "1815-12-10");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.username").isEqualTo(renamed);
    assertThat(result).bodyJson().extractingPath("$.fullName").isEqualTo("Ada Lovelace");
    assertThat(result).bodyJson().extractingPath("$.birthDate").isEqualTo("1815-12-10");
    assertThat(login(renamed)).hasStatusOk();
  }

  @Test
  void keepingTheSameUsernameIsFine() {
    String username = newUsername();
    String accessToken = signUp(username);

    assertThat(updateProfile(accessToken, username, "New Name", "1990-05-17"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.fullName")
        .isEqualTo("New Name");
  }

  @Test
  void aUsernameAnotherAccountHasIsTakenInAnyCase() {
    String taken = newUsername();
    signUp(taken);
    String accessToken = signUp(newUsername());

    assertThat(
            updateProfile(accessToken, taken.toUpperCase(Locale.ROOT), "Test User", "1990-05-17"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("USERNAME_TAKEN");
  }

  @Test
  void theProfileFollowsTheSignUpRules() {
    String accessToken = signUp(newUsername());

    assertThat(updateProfile(accessToken, "a b", "Test User", "1990-05-17"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
    assertThat(updateProfile(accessToken, newUsername(), " ", "1990-05-17"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(updateProfile(accessToken, newUsername(), "Test User", "2999-01-01"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void needsASignedInUser() {
    assertThat(
            mvc.put()
                .uri("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileJson(newUsername(), "Test User", "1990-05-17"))
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MvcTestResult updateProfile(
      String accessToken, String username, String fullName, String birthDate) {
    return mvc.put()
        .uri("/api/v1/me")
        .cookie(new Cookie(ACCESS, accessToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(profileJson(username, fullName, birthDate))
        .exchange();
  }

  private static String profileJson(String username, String fullName, String birthDate) {
    return """
        {"username": "%s", "fullName": "%s", "birthDate": "%s"}
        """
        .formatted(username, fullName, birthDate);
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

  private MvcTestResult login(String username) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\": \"%s\", \"password\": \"correct horse\"}".formatted(username))
        .exchange();
  }

  private static String newUsername() {
    return "prof_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  }
}
