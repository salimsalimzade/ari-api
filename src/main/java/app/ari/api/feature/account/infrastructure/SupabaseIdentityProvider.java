package app.ari.api.feature.account.infrastructure;

import app.ari.api.common.exception.TooManyRequestsException;
import app.ari.api.config.AuthProperties;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.port.IdentityProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * {@link IdentityProvider} over Supabase Auth's REST API (GoTrue), with the project's secret key.
 * Every call carries the browser's address in {@code Sb-Forwarded-For}, so Supabase's per-address
 * rate limits count each user instead of ari-api as one (a hosted project must allow it under
 * Authentication, Rate Limits).
 */
@Component
public class SupabaseIdentityProvider implements IdentityProvider {

  private static final String FORWARDED_FOR = "Sb-Forwarded-For";

  private final RestClient http;
  private final AuthProperties properties;
  private final JsonMapper jsonMapper;

  public SupabaseIdentityProvider(AuthProperties properties, JsonMapper jsonMapper) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
    requests.setReadTimeout(Duration.ofSeconds(10));
    this.http =
        RestClient.builder()
            .baseUrl(properties.authUrl())
            .requestFactory(requests)
            .defaultHeader("apikey", properties.supabaseSecretKey())
            .requestInterceptor(forwardClientAddress())
            .build();
  }

  @Override
  public Outcome createUser(UUID id, String email, String password, Map<String, String> metadata) {
    Map<String, Object> body = new HashMap<>();
    body.put("id", id.toString());
    body.put("email", email);
    body.put("password", password);
    body.put("email_confirm", true);
    body.put("user_metadata", metadata);
    Reply reply = send(http.post().uri("/admin/users").headers(this::asAdmin).body(body));
    if (reply.ok()) {
      return Outcome.DONE;
    }
    if (reply.errorCode().equals("weak_password")) {
      return Outcome.WEAK_PASSWORD;
    }
    if (reply.isConflict()) {
      return Outcome.TAKEN;
    }
    throw reply.unexpected("create a user");
  }

  @Override
  public Optional<IssuedTokens> signIn(String email, String password) {
    return tokens(
        http.post()
            .uri("/token?grant_type=password")
            .body(Map.of("email", email, "password", password)),
        "sign in");
  }

  @Override
  public Optional<IssuedTokens> refresh(String refreshToken) {
    return tokens(
        http.post()
            .uri("/token?grant_type=refresh_token")
            .body(Map.of("refresh_token", refreshToken)),
        "refresh a session");
  }

  @Override
  public Optional<IssuedTokens> newSession(UUID userId) {
    Optional<String> email = email(userId);
    if (email.isEmpty()) {
      return Optional.empty();
    }
    Reply link =
        send(
            http.post()
                .uri("/admin/generate_link")
                .headers(this::asAdmin)
                .body(Map.of("type", "magiclink", "email", email.get())));
    if (link.status().value() == HttpStatus.NOT_FOUND.value()) {
      return Optional.empty();
    }
    if (!link.ok()) {
      throw link.unexpected("make a sign-in link");
    }
    return tokens(
        http.post()
            .uri("/verify")
            .body(
                Map.of(
                    "type",
                    "magiclink",
                    "token_hash",
                    link.body().path("hashed_token").asString())),
        "use a sign-in link");
  }

  @Override
  public URI googleSignInUrl(String redirectTo, String codeChallenge) {
    return UriComponentsBuilder.fromUriString(properties.publicAuthUrl())
        .path("/authorize")
        .queryParam("provider", "google")
        .queryParam("redirect_to", redirectTo)
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "s256")
        .encode()
        .build()
        .toUri();
  }

  @Override
  public Optional<IssuedTokens> exchangeCode(String authCode, String codeVerifier) {
    return tokens(
        http.post()
            .uri("/token?grant_type=pkce")
            .body(Map.of("auth_code", authCode, "code_verifier", codeVerifier)),
        "finish a Google sign-in");
  }

  @Override
  public boolean signOut(String accessToken, boolean everywhere) {
    Reply reply =
        send(
            http.post()
                .uri("/logout?scope={scope}", everywhere ? "global" : "local")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    if (reply.ok()) {
      return true;
    }
    if (reply.status().is4xxClientError() && reply.status().value() != 429) {
      return false;
    }
    throw reply.unexpected("sign out");
  }

  @Override
  public Optional<String> email(UUID userId) {
    Reply reply = send(http.get().uri("/admin/users/{id}", userId).headers(this::asAdmin));
    if (reply.status().value() == HttpStatus.NOT_FOUND.value()) {
      return Optional.empty();
    }
    if (!reply.ok()) {
      throw reply.unexpected("read a user");
    }
    return Optional.of(reply.body().path("email").asString());
  }

  @Override
  public Outcome setEmail(UUID userId, String email) {
    Reply reply =
        send(
            http.put()
                .uri("/admin/users/{id}", userId)
                .headers(this::asAdmin)
                .body(Map.of("email", email, "email_confirm", true)));
    if (reply.ok()) {
      return Outcome.DONE;
    }
    if (reply.isConflict()) {
      return Outcome.TAKEN;
    }
    throw reply.unexpected("change a user's email");
  }

  @Override
  public Outcome setPassword(UUID userId, String password) {
    Reply reply =
        send(
            http.put()
                .uri("/admin/users/{id}", userId)
                .headers(this::asAdmin)
                .body(Map.of("password", password)));
    if (reply.ok()) {
      return Outcome.DONE;
    }
    if (reply.errorCode().equals("weak_password")) {
      return Outcome.WEAK_PASSWORD;
    }
    throw reply.unexpected("change a user's password");
  }

  /** The admin API also wants the secret key as the bearer token. */
  private void asAdmin(HttpHeaders headers) {
    headers.setBearerAuth(properties.supabaseSecretKey());
  }

  /** A session from a token endpoint; empty for a 4xx (wrong password, used code or token). */
  private Optional<IssuedTokens> tokens(RestClient.RequestHeadersSpec<?> spec, String action) {
    Reply reply = send(spec);
    if (reply.ok()) {
      JsonNode body = reply.body();
      return Optional.of(
          new IssuedTokens(
              UUID.fromString(body.path("user").path("id").asString()),
              body.path("access_token").asString(),
              body.path("refresh_token").asString(),
              Duration.ofSeconds(body.path("expires_in").asLong())));
    }
    if (reply.status().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
      throw new TooManyRequestsException(
          "RATE_LIMITED", "Too many sign-in attempts. Wait a few minutes and try again.");
    }
    if (reply.status().is4xxClientError()) {
      return Optional.empty();
    }
    throw reply.unexpected(action);
  }

  private Reply send(RestClient.RequestHeadersSpec<?> spec) {
    return spec.exchange(
        (request, response) -> {
          byte[] body = response.getBody().readAllBytes();
          return new Reply(response.getStatusCode(), parse(body));
        });
  }

  /** Nothing, or not JSON (a gateway's error page), reads as a missing node. */
  private JsonNode parse(byte[] body) {
    if (body.length == 0) {
      return MissingNode.getInstance();
    }
    try {
      return jsonMapper.readTree(body);
    } catch (JacksonException e) {
      return MissingNode.getInstance();
    }
  }

  /** The browser's address, for Supabase's rate limits; none outside a request. */
  private static ClientHttpRequestInterceptor forwardClientAddress() {
    return (request, body, execution) -> {
      if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes current) {
        HttpServletRequest incoming = current.getRequest();
        request.getHeaders().set(FORWARDED_FOR, incoming.getRemoteAddr());
      }
      return execution.execute(request, body);
    };
  }

  /** Supabase Auth's answer: its errors have an {@code error_code} (or a database {@code code}). */
  private record Reply(HttpStatusCode status, JsonNode body) {

    boolean ok() {
      return status.is2xxSuccessful();
    }

    String errorCode() {
      return body.path("error_code").asString("");
    }

    /**
     * Another user has the id or email: Supabase answers {@code email_exists}, or passes on the
     * database's unique violation (23505), also from the profile trigger (a taken username).
     */
    boolean isConflict() {
      String message = body.path("msg").asString("") + body.path("message").asString("");
      return errorCode().equals("email_exists")
          || body.path("code").asString("").equals("23505")
          || message.startsWith("Database error");
    }

    IllegalStateException unexpected(String action) {
      return new IllegalStateException(
          "Supabase Auth could not " + action + ": " + status.value() + " " + body);
    }
  }
}
