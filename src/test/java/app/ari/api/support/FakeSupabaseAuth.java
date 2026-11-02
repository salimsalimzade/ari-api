package app.ari.api.support;

import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.port.IdentityProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Supabase Auth for the tests, in memory: users and sessions in maps, access tokens signed with a
 * key of its own (the test decoder trusts it), refresh tokens that work once. Every user is also
 * inserted into {@code auth.users}, so the real profile trigger makes its users row; a refused row
 * (a taken username) fails like Supabase's "Database error".
 */
public class FakeSupabaseAuth implements IdentityProvider {

  public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final String issuer;
  private final JsonMapper json = JsonMapper.builder().build();
  private final RSAKey key;

  private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
  private final Map<String, UUID> sessionsByRefreshToken = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> userBySession = new ConcurrentHashMap<>();
  private final Map<String, GoogleCode> googleCodes = new ConcurrentHashMap<>();

  private record Account(String email, String password) {}

  private record GoogleCode(String email, String name, String codeChallenge) {}

  public FakeSupabaseAuth(JdbcTemplate jdbc, Clock clock, String issuer) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.issuer = issuer;
    try {
      this.key = new RSAKeyGenerator(2048).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public RSAPublicKey publicKey() {
    try {
      return key.toRSAPublicKey();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * What Google sends back after its user (with this email and name) agreed, for the browser whose
   * verifier this is. Like Supabase, a Google email that a user already signs in with links to that
   * user.
   */
  public String googleCode(String email, String name, String codeVerifier) {
    String code = UUID.randomUUID().toString();
    googleCodes.put(code, new GoogleCode(email, name, challenge(codeVerifier)));
    return code;
  }

  @Override
  public Outcome createUser(UUID id, String email, String password, Map<String, String> metadata) {
    if (accounts.containsKey(id) || findByEmail(email).isPresent()) {
      return Outcome.TAKEN;
    }
    if (password.length() < 8) {
      return Outcome.WEAK_PASSWORD;
    }
    if (!insertAuthUser(id, email, "email", metadata)) {
      return Outcome.TAKEN;
    }
    accounts.put(id, new Account(email, password));
    return Outcome.DONE;
  }

  @Override
  public Optional<IssuedTokens> signIn(String email, String password) {
    return findByEmail(email)
        .filter(id -> accounts.get(id).password().equals(password))
        .map(this::startSession);
  }

  @Override
  public Optional<IssuedTokens> refresh(String refreshToken) {
    UUID session = sessionsByRefreshToken.remove(refreshToken);
    if (session == null || !userBySession.containsKey(session)) {
      return Optional.empty();
    }
    return Optional.of(tokens(userBySession.get(session), session));
  }

  @Override
  public Optional<IssuedTokens> newSession(UUID userId) {
    return accounts.containsKey(userId) ? Optional.of(startSession(userId)) : Optional.empty();
  }

  @Override
  public URI googleSignInUrl(String redirectTo, String codeChallenge) {
    return UriComponentsBuilder.fromUriString(issuer)
        .path("/authorize")
        .queryParam("provider", "google")
        .queryParam("redirect_to", redirectTo)
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "s256")
        .build()
        .toUri();
  }

  @Override
  public Optional<IssuedTokens> exchangeCode(String authCode, String codeVerifier) {
    GoogleCode code = googleCodes.remove(authCode);
    if (code == null || !code.codeChallenge().equals(challenge(codeVerifier))) {
      return Optional.empty();
    }
    UUID id =
        findByEmail(code.email())
            .orElseGet(
                () -> {
                  UUID created = UUID.randomUUID();
                  insertAuthUser(created, code.email(), "google", Map.of("full_name", code.name()));
                  accounts.put(created, new Account(code.email(), UUID.randomUUID().toString()));
                  return created;
                });
    return Optional.of(startSession(id));
  }

  @Override
  public boolean signOut(String accessToken, boolean everywhere) {
    Optional<UUID> session = sessionOf(accessToken);
    if (session.isEmpty() || !userBySession.containsKey(session.get())) {
      return false;
    }
    UUID userId = userBySession.remove(session.get());
    if (everywhere) {
      userBySession.values().removeIf(userId::equals);
    }
    return true;
  }

  @Override
  public Optional<String> email(UUID userId) {
    return Optional.ofNullable(accounts.get(userId)).map(Account::email);
  }

  @Override
  public Outcome setEmail(UUID userId, String email) {
    if (findByEmail(email).filter(owner -> !owner.equals(userId)).isPresent()) {
      return Outcome.TAKEN;
    }
    accounts.computeIfPresent(userId, (id, account) -> new Account(email, account.password()));
    return Outcome.DONE;
  }

  @Override
  public Outcome setPassword(UUID userId, String password) {
    if (password.length() < 8) {
      return Outcome.WEAK_PASSWORD;
    }
    accounts.computeIfPresent(userId, (id, account) -> new Account(account.email(), password));
    return Outcome.DONE;
  }

  private Optional<UUID> findByEmail(String email) {
    return accounts.entrySet().stream()
        .filter(entry -> entry.getValue().email().equalsIgnoreCase(email))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /** The row Supabase would write; false when the profile trigger refused it. */
  private boolean insertAuthUser(
      UUID id, String email, String provider, Map<String, String> metadata) {
    try {
      jdbc.update(
          "INSERT INTO auth.users (id, aud, role, email, raw_app_meta_data, raw_user_meta_data,"
              + " created_at, updated_at) VALUES (?, 'authenticated', 'authenticated', ?,"
              + " ?::jsonb, ?::jsonb, now(), now())",
          id,
          email,
          json.writeValueAsString(Map.of("provider", provider)),
          json.writeValueAsString(metadata));
      return true;
    } catch (DataAccessException e) {
      return false;
    }
  }

  private IssuedTokens startSession(UUID userId) {
    UUID session = UUID.randomUUID();
    userBySession.put(session, userId);
    return tokens(userId, session);
  }

  private IssuedTokens tokens(UUID userId, UUID session) {
    String refreshToken = UUID.randomUUID().toString();
    sessionsByRefreshToken.put(refreshToken, session);
    return new IssuedTokens(userId, accessToken(userId, session), refreshToken, ACCESS_TOKEN_TTL);
  }

  private String accessToken(UUID userId, UUID session) {
    Instant now = clock.instant();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(userId.toString())
            .audience("authenticated")
            .claim("role", "authenticated")
            .claim("session_id", session.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ACCESS_TOKEN_TTL)))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(key));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    return jwt.serialize();
  }

  /** The session an unexpired access token of ours belongs to. */
  private Optional<UUID> sessionOf(String accessToken) {
    try {
      JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
      if (!clock.instant().isBefore(claims.getExpirationTime().toInstant())) {
        return Optional.empty();
      }
      return Optional.of(
          UUID.fromString(Objects.requireNonNull(claims.getStringClaim("session_id"))));
    } catch (java.text.ParseException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private static String challenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
