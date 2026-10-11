package app.ari.api.feature.account.presentation.google;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static app.ari.api.feature.account.presentation.cookie.AuthCookies.REFRESH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ari.api.feature.account.application.exception.unauthorized.GoogleSignInRejectedException;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.account.application.model.request.GoogleProfile;
import app.ari.api.feature.account.domain.model.Plan;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class GoogleSignInTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private AccountFacade facade;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private Clock clock;
  @Autowired private MockMvcTester mvc;
  @Autowired private GoogleSignInSuccessHandler successHandler;
  @Autowired private GoogleSignInFailureHandler failureHandler;

  @Test
  void startsTheHandshakeWithGoogle() {
    MvcTestResult result = mvc.get().uri("/oauth2/authorization/google").exchange();

    assertThat(result).hasStatus(HttpStatus.FOUND);
    assertThat(result.getResponse().getRedirectedUrl())
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        .contains("client_id=test-google-client-id", "scope=openid", "state=");
  }

  @Test
  void aNewGoogleUserGetsAnAccountWithoutPassword() {
    String email = newEmail();

    facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "Ada Lovelace"));

    User user = users.findByEmail(email).orElseThrow();
    assertThat(user.passwordHash()).isNull();
    assertThat(user.emailVerifiedAt()).isNotNull();
    assertThat(user.fullName()).isEqualTo("Ada Lovelace");
    assertThat(user.birthDate()).isNull();
    assertThat(user.plan()).isEqualTo(Plan.FREE);
  }

  @Test
  void aNewGoogleUserGetsAUsernameMadeFromTheEmail() {
    String local = "ada.love-" + UUID.randomUUID().toString().substring(0, 8);

    facade.signInWithGoogle(
        new GoogleProfile(newSubject(), local + "@example.com", true, "Ada Lovelace"));

    User user = users.findByEmail(local + "@example.com").orElseThrow();
    assertThat(user.username()).isEqualTo(local.replace('-', '_'));
  }

  @Test
  void twoGoogleUsersWithTheSameEmailNameGetDifferentUsernames() {
    String local = "twin" + UUID.randomUUID().toString().substring(0, 8);

    facade.signInWithGoogle(
        new GoogleProfile(newSubject(), local + "@example.com", true, "First Twin"));
    facade.signInWithGoogle(
        new GoogleProfile(newSubject(), local + "@example.org", true, "Second Twin"));

    assertThat(users.findByEmail(local + "@example.com").orElseThrow().username()).isEqualTo(local);
    assertThat(users.findByEmail(local + "@example.org").orElseThrow().username())
        .isEqualTo(local + "2");
  }

  @Test
  void theSameGoogleAccountAlwaysFindsTheSameUser() {
    String subject = newSubject();
    String email = newEmail();
    facade.signInWithGoogle(new GoogleProfile(subject, email, true, "Ada"));

    // The Google account changed its email: we still match it by subject.
    String newEmail = newEmail();
    facade.signInWithGoogle(new GoogleProfile(subject, newEmail, true, "Ada"));

    assertThat(users.findByEmail(newEmail)).isEmpty();
  }

  @Test
  void aVerifiedPasswordAccountIsLinkedAndKeepsItsPassword() {
    String email = newEmail();
    Instant now = clock.instant();
    users.insert(
        new User(
            UUID.randomUUID(),
            email,
            "verified_" + UUID.randomUUID().toString().substring(0, 8),
            passwordEncoder.encode(PASSWORD),
            "Verified",
            LocalDate.of(1990, 5, 17),
            Plan.FREE,
            0,
            now,
            now,
            now));

    facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "Verified"));

    assertThat(login(email, PASSWORD)).hasStatusOk();
  }

  @Test
  void anUnverifiedPasswordAccountLosesItsPasswordAndSessionsWhenGoogleProvesTheEmail() {
    String email = newEmail();
    String refreshToken = cookie(register(email), REFRESH);

    facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "Owner"));

    User user = users.findByEmail(email).orElseThrow();
    assertThat(user.passwordHash()).isNull();
    assertThat(user.emailVerifiedAt()).isNotNull();
    assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, refreshToken)))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anEmailGoogleHasNotVerifiedIsRefused() {
    String email = newEmail();

    assertThatThrownBy(
            () -> facade.signInWithGoogle(new GoogleProfile(newSubject(), email, false, "X")))
        .isInstanceOf(GoogleSignInRejectedException.class);
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void aUserCanLinkOnlyOneGoogleAccount() {
    String email = newEmail();
    facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "First"));

    assertThatThrownBy(
            () -> facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "Second")))
        .isInstanceOf(GoogleSignInRejectedException.class);
  }

  @Test
  void aGoogleOnlyUserCannotUseThePasswordEndpoints() {
    String email = newEmail();
    facade.signInWithGoogle(new GoogleProfile(newSubject(), email, true, "Google Only"));

    MvcTestResult loginResult = login(email, PASSWORD);
    assertThat(loginResult).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(loginResult).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    assertThat(register(email)).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void successSetsTheCookiesAndOpensTheLibrary() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    successHandler.onAuthenticationSuccess(
        new MockHttpServletRequest(), response, googleLogin(newEmail(), true));

    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/library");
    assertThat(response.getCookie(ACCESS)).isNotNull();
    assertThat(response.getCookie(REFRESH)).isNotNull();
  }

  @Test
  void aRefusedSignInGoesBackToTheLoginPage() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    successHandler.onAuthenticationSuccess(
        new MockHttpServletRequest(), response, googleLogin(newEmail(), false));

    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=google");
    assertThat(response.getCookie(ACCESS)).isNull();
  }

  @Test
  void aFailedHandshakeGoesBackToTheLoginPage() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    failureHandler.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=google");
  }

  private static OAuth2AuthenticationToken googleLogin(String email, boolean emailVerified) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("id-token")
            .subject(newSubject())
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .claim("name", "Ada Lovelace")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    DefaultOidcUser user = new DefaultOidcUser(List.of(), idToken);
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
  }

  private MvcTestResult register(String email) {
    return mvc.post()
        .uri("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "%s", "username": "%s", "password": "%s", "fullName": "Test User",
             "birthDate": "1990-05-17"}
            """
                .formatted(email, "user_" + UUID.randomUUID().toString().substring(0, 8), PASSWORD))
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

  private static String cookie(MvcTestResult result, String name) {
    Cookie cookie = result.getResponse().getCookie(name);
    assertThat(cookie).as("cookie %s", name).isNotNull();
    return cookie.getValue();
  }

  private static String newEmail() {
    return "google-" + UUID.randomUUID() + "@example.com";
  }

  private static String newSubject() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
