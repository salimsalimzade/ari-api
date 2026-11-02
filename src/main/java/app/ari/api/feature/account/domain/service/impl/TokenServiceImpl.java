package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.feature.account.domain.model.GoogleSignIn;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.NoEmail;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.port.IdentityProvider;
import app.ari.api.feature.account.domain.repository.ExtensionCodeRepository;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenServiceImpl implements TokenService {

  /**
   * Long enough for the website tab to hand the code to the extension, which redeems it at once.
   */
  static final Duration EXTENSION_CODE_LIFETIME = Duration.ofSeconds(60);

  private final IdentityProvider identity;
  private final UserRepository users;
  private final ExtensionCodeRepository extensionCodes;
  private final SecureRandom random = new SecureRandom();

  public TokenServiceImpl(
      IdentityProvider identity, UserRepository users, ExtensionCodeRepository extensionCodes) {
    this.identity = identity;
    this.users = users;
    this.extensionCodes = extensionCodes;
  }

  @Override
  public Optional<IssuedTokens> signIn(String username, String password) {
    Optional<UUID> id = users.findByUsername(Usernames.normalize(username)).map(user -> user.id());
    if (id.isEmpty()) {
      // An address nobody has, so the answer comes from Supabase like a wrong password's.
      identity.signIn(NoEmail.of(UUID.randomUUID()), password);
      return Optional.empty();
    }
    return identity.email(id.get()).flatMap(email -> identity.signIn(email, password));
  }

  @Override
  public Optional<IssuedTokens> rotate(String refreshToken) {
    return identity.refresh(refreshToken);
  }

  @Override
  public void revokeSession(String accessToken, String refreshToken) {
    if (StringUtils.hasText(accessToken) && identity.signOut(accessToken, false)) {
      return;
    }
    // Supabase signs out with an access token; a working refresh token still gets one.
    if (StringUtils.hasText(refreshToken)) {
      identity
          .refresh(refreshToken)
          .ifPresent(tokens -> identity.signOut(tokens.accessToken(), false));
    }
  }

  @Override
  public String issueExtensionCode(UUID userId) {
    String code = newRandomToken();
    extensionCodes.save(hash(code), userId, EXTENSION_CODE_LIFETIME);
    return code;
  }

  @Override
  public Optional<IssuedTokens> redeemExtensionCode(String code) {
    return extensionCodes.take(hash(code)).flatMap(identity::newSession);
  }

  @Override
  public GoogleSignIn startGoogleSignIn(String redirectTo) {
    String verifier = newRandomToken();
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    return new GoogleSignIn(identity.googleSignInUrl(redirectTo, challenge), verifier);
  }

  @Override
  public Optional<IssuedTokens> finishGoogleSignIn(String authCode, String codeVerifier) {
    if (!StringUtils.hasText(authCode) || !StringUtils.hasText(codeVerifier)) {
      return Optional.empty();
    }
    return identity.exchangeCode(authCode, codeVerifier);
  }

  /** 32 random bytes, base64url: an extension code or a PKCE verifier (43 characters). */
  private String newRandomToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    return HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
