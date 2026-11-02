package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static app.ari.api.feature.account.presentation.cookie.AuthCookies.GOOGLE_VERIFIER;
import static app.ari.api.feature.account.presentation.cookie.AuthCookies.REFRESH;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.FakeSupabaseAuth;
import app.ari.api.support.IntegrationTest;
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

@IntegrationTest
class GoogleSignInTest {

  @Autowired private MockMvcTester mvc;
  @Autowired private FakeSupabaseAuth supabase;
  @Autowired private JsonMapper jsonMapper;

  @Test
  void startingGoesToGoogleThroughSupabaseWithAPkceChallenge() {
    MvcTestResult result = mvc.get().uri("/api/v1/auth/google").exchange();

    assertThat(result).hasStatus(HttpStatus.FOUND);
    assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
        .startsWith("http://supabase.test/auth/v1/authorize?provider=google")
        .contains("redirect_to=http://localhost:8080/api/v1/auth/google/callback")
        .contains("code_challenge_method=s256");
    assertThat(result)
        .cookies()
        .isHttpOnly(GOOGLE_VERIFIER, true)
        .hasPath(GOOGLE_VERIFIER, "/api/v1/auth/google")
        .hasMaxAge(GOOGLE_VERIFIER, Duration.ofMinutes(10));
  }

  @Test
  void aNewGoogleUserGetsAnAccountAndChoosesAUsernameFirst() {
    String email = "grace" + unique() + "@gmail.com";

    MvcTestResult result = signInWithGoogle(email, "Grace Hopper");

    assertThat(result).hasStatus(HttpStatus.FOUND);
    assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost:3000/welcome");
    assertThat(result).cookies().containsCookies(ACCESS, REFRESH);
    assertThat(result).cookies().hasMaxAge(GOOGLE_VERIFIER, Duration.ZERO);

    Cookie access = new Cookie(ACCESS, result.getResponse().getCookie(ACCESS).getValue());
    MvcTestResult me = mvc.get().uri("/api/v1/me").cookie(access).exchange();
    assertThat(me).bodyJson().extractingPath("$.needsUsername").isEqualTo(true);
    assertThat(me).bodyJson().extractingPath("$.fullName").isEqualTo("Grace Hopper");
    assertThat(me).bodyJson().extractingPath("$.email").isEqualTo(email);
    assertThat(me)
        .bodyJson()
        .extractingPath("$.username")
        .asString()
        .startsWith(email.substring(0, Math.min(20, email.indexOf('@'))));

    MvcTestResult chosen =
        mvc.put()
            .uri("/api/v1/me")
            .cookie(access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"username": "%s", "fullName": "Grace Hopper", "birthDate": "1906-12-09"}
                """
                    .formatted("grace_" + unique()))
            .exchange();
    assertThat(chosen).hasStatusOk();
    assertThat(chosen).bodyJson().extractingPath("$.needsUsername").isEqualTo(false);

    // The next Google sign-in is the same account, straight to the Library.
    MvcTestResult again = signInWithGoogle(email, "Grace Hopper");
    assertThat(again.getResponse().getHeader(HttpHeaders.LOCATION))
        .isEqualTo("http://localhost:3000/library");
  }

  @Test
  void twoGoogleUsersWithTheSameNameGetDifferentUsernames() {
    String name = "ada" + unique();

    String first = username(signInWithGoogle(name + "@gmail.com", "Ada One"));
    String second = username(signInWithGoogle(name + "@example.com", "Ada Two"));

    assertThat(first).isEqualTo(name);
    assertThat(second).startsWith(name + "_").isNotEqualTo(first);
  }

  @Test
  void anotherBrowsersVerifierOrAMissingCodeGoesBackToTheLogInPage() {
    String code = supabase.googleCode("mallory" + unique() + "@gmail.com", "M", "their-verifier");

    for (MvcTestResult result :
        new MvcTestResult[] {
          mvc.get()
              .uri("/api/v1/auth/google/callback?code={code}", code)
              .cookie(new Cookie(GOOGLE_VERIFIER, "my-verifier"))
              .exchange(),
          mvc.get().uri("/api/v1/auth/google/callback?error=access_denied").exchange()
        }) {
      assertThat(result).hasStatus(HttpStatus.FOUND);
      assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
          .isEqualTo("http://localhost:3000/login?error=google");
      assertThat(result).cookies().doesNotContainCookie(ACCESS);
    }
  }

  /** Leaves for Google, then comes back as Google would after its user agreed. */
  private MvcTestResult signInWithGoogle(String email, String name) {
    MvcTestResult start = mvc.get().uri("/api/v1/auth/google").exchange();
    String verifier = start.getResponse().getCookie(GOOGLE_VERIFIER).getValue();
    return mvc.get()
        .uri("/api/v1/auth/google/callback?code={code}", supabase.googleCode(email, name, verifier))
        .cookie(new Cookie(GOOGLE_VERIFIER, verifier))
        .exchange();
  }

  private String username(MvcTestResult signedIn) {
    Cookie access = new Cookie(ACCESS, signedIn.getResponse().getCookie(ACCESS).getValue());
    MvcTestResult me = mvc.get().uri("/api/v1/me").cookie(access).exchange();
    return jsonMapper.readTree(me.getResponse().getContentAsByteArray()).get("username").asString();
  }

  private static String unique() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
