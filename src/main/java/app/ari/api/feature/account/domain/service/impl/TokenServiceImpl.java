package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.RefreshToken;
import app.ari.api.feature.account.domain.model.TokenClient;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.RefreshTokenRepository;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenServiceImpl implements TokenService {

  static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

  /**
   * How long a replaced refresh token still works, for two requests that refresh at once (a server
   * render and a browser call). Only while its session is alive, so logout stays final.
   */
  static final Duration REUSE_GRACE = Duration.ofSeconds(10);

  private static final Logger log = LoggerFactory.getLogger(TokenServiceImpl.class);

  private final JwtEncoder jwtEncoder;
  private final RefreshTokenRepository refreshTokens;
  private final UserRepository users;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public TokenServiceImpl(
      JwtEncoder jwtEncoder,
      RefreshTokenRepository refreshTokens,
      UserRepository users,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.refreshTokens = refreshTokens;
    this.users = users;
    this.clock = clock;
  }

  @Override
  public IssuedTokens issue(User user, TokenClient client) {
    return issue(user, client, UUID.randomUUID(), clock.instant());
  }

  @Override
  @Transactional
  public Optional<IssuedTokens> rotate(String refreshToken) {
    Optional<RefreshToken> found = refreshTokens.findByTokenHash(hash(refreshToken));
    if (found.isEmpty()) {
      return Optional.empty();
    }
    RefreshToken token = found.get();
    Instant now = clock.instant();
    if (!now.isBefore(token.expiresAt())) {
      return Optional.empty();
    }
    if (!refreshTokens.revoke(token.id(), now) && !withinReuseGrace(token, now)) {
      log.warn("Refresh token reused; revoking session {}", token.familyId());
      refreshTokens.revokeFamily(token.familyId(), now);
      return Optional.empty();
    }
    return users
        .findById(token.userId())
        .map(user -> issue(user, token.client(), token.familyId(), now));
  }

  @Override
  @Transactional
  public void revokeSession(String refreshToken) {
    refreshTokens
        .findByTokenHash(hash(refreshToken))
        .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
  }

  private IssuedTokens issue(User user, TokenClient client, UUID familyId, Instant now) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_TTL))
            .claim("plan", user.plan().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String accessToken =
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    String refreshToken = newRefreshToken();
    refreshTokens.insert(
        new RefreshToken(
            UUID.randomUUID(),
            user.id(),
            hash(refreshToken),
            familyId,
            client,
            now.plus(REFRESH_TOKEN_TTL),
            null,
            now));
    return new IssuedTokens(accessToken, refreshToken);
  }

  /**
   * The token was replaced moments ago (or just now, by a parallel request) and its session is
   * alive.
   */
  private boolean withinReuseGrace(RefreshToken token, Instant now) {
    Instant revokedAt = token.revokedAt() != null ? token.revokedAt() : now;
    return !now.isAfter(revokedAt.plus(REUSE_GRACE))
        && refreshTokens.hasActiveToken(token.familyId(), now);
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
