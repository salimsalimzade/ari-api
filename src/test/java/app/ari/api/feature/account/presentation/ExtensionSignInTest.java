package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static app.ari.api.feature.account.presentation.cookie.AuthCookies.REFRESH;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * F13: the signed-in website hands the extension a code, and the extension keeps its own session.
 */
@IntegrationTest
class ExtensionSignInTest {

  private static final String EXTENSION_ORIGIN =
      "moz-extension://4f8c0f1e-0000-4000-8000-000000000000";

  @Autowired private MockMvcTester mvc;
  @Autowired private MutableClock clock;
  @Autowired private JsonMapper jsonMapper;

  @Test
  void theWebsitesCodeSignsTheExtensionInOnce() {
    String username = newUsername();
    String code = extensionCode(register(username));

    MvcTestResult connected = connect(code);

    assertThat(connected).hasStatusOk();
    assertThat(connected.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    assertThat(
            mvc.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + json(connected, "accessToken")))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.username")
        .isEqualTo(username);
    assertThat(connect(code))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CODE");
  }

  @Test
  void anotherAccountsPasswordGivesACodeWithoutTouchingTheWebsite() {
    String website = newUsername();
    String other = newUsername();
    register(website);
    register(other);

    MvcTestResult result = codeFor(other, "correct horse");

    assertThat(result).hasStatusOk();
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    MvcTestResult connected = connect(json(result, "code"));
    assertThat(
            mvc.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + json(connected, "accessToken")))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.username")
        .isEqualTo(other);
  }

  @Test
  void aWrongPasswordGetsNoCode() {
    String username = newUsername();
    register(username);

    for (MvcTestResult result :
        new MvcTestResult[] {codeFor(username, "wrong password"), codeFor(newUsername(), "x")}) {
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }
  }

  @Test
  void aCodeNeedsASignedInWebsite() {
    assertThat(mvc.post().uri("/api/v1/me/extension-code")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(connect("made-up-code")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theExtensionRefreshesItsOwnTokens() {
    MvcTestResult connected = connect(extensionCode(register(newUsername())));
    String first = json(connected, "refreshToken");

    MvcTestResult refreshed = refresh(first);

    assertThat(refreshed).hasStatusOk();
    assertThat(refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    String second = json(refreshed, "refreshToken");
    assertThat(second).isNotEqualTo(first);
    assertThat(refresh(second)).hasStatusOk();

    clock.advance(Duration.ofSeconds(11));
    MvcTestResult reused = refresh(first);
    assertThat(reused).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(reused).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    assertThat(reused.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void websiteAndExtensionTokensDontMix() {
    MvcTestResult website = register(newUsername());
    String extensionRefresh = json(connect(extensionCode(website)), "refreshToken");

    assertThat(refresh(cookie(website, REFRESH))).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, extensionRefresh)))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutEndsOnlyTheExtensionsSession() {
    MvcTestResult website = register(newUsername());
    String extensionRefresh = json(connect(extensionCode(website)), "refreshToken");

    assertThat(
            mvc.post()
                .uri("/api/v1/auth/extension/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(extensionRefresh)))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(refresh(extensionRefresh)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/refresh")
                .cookie(new Cookie(REFRESH, cookie(website, REFRESH))))
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void theExtensionMayCallTheApiWithoutCookies() {
    assertThat(
            mvc.options()
                .uri("/api/v1/auth/extension/refresh")
                .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN)
        .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
    // Firefox sends the origin on the request itself too, which must not be turned away.
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/extension/token")
                .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"made-up-code\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.options()
                .uri("/api/v1/me")
                .header(HttpHeaders.ORIGIN, "https://elsewhere.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  private MvcTestResult register(String username) {
    return mvc.post()
        .uri("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username": "%s", "password": "correct horse", "fullName": "Test User",
             "birthDate": "1990-05-17"}
            """
                .formatted(username))
        .exchange();
  }

  private String extensionCode(MvcTestResult website) {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/me/extension-code")
            .cookie(new Cookie(ACCESS, cookie(website, ACCESS)))
            .exchange();
    assertThat(result).hasStatusOk();
    return json(result, "code");
  }

  private MvcTestResult codeFor(String username, String password) {
    return mvc.post()
        .uri("/api/v1/auth/extension/code")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username": "%s", "password": "%s"}
            """
                .formatted(username, password))
        .exchange();
  }

  private MvcTestResult connect(String code) {
    return mvc.post()
        .uri("/api/v1/auth/extension/token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\": \"%s\"}".formatted(code))
        .exchange();
  }

  private MvcTestResult refresh(String refreshToken) {
    return mvc.post()
        .uri("/api/v1/auth/extension/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(refreshBody(refreshToken))
        .exchange();
  }

  private static String refreshBody(String refreshToken) {
    return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
  }

  private String json(MvcTestResult result, String field) {
    return jsonMapper.readTree(result.getResponse().getContentAsByteArray()).get(field).asString();
  }

  private static String cookie(MvcTestResult result, String name) {
    Cookie cookie = result.getResponse().getCookie(name);
    assertThat(cookie).as("cookie %s", name).isNotNull();
    return cookie.getValue();
  }

  private static String newUsername() {
    return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
  }
}
