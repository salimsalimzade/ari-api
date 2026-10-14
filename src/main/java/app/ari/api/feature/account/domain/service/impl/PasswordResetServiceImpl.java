package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.common.ratelimit.RateLimiter;
import app.ari.api.feature.account.domain.model.PasswordResetCode;
import app.ari.api.feature.account.domain.model.ResetLink;
import app.ari.api.feature.account.domain.model.ResetRecipient;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.repository.PasswordResetCodeRepository;
import app.ari.api.feature.account.domain.repository.RefreshTokenRepository;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.PasswordResetService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * A link is kept a week after it was made, though it only works for 30 minutes, so the page can
 * tell an expired or used link from one that never existed.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

  private static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
  private static final Duration KEPT_FOR = Duration.ofDays(7);

  private final UserRepository users;
  private final PasswordResetCodeRepository codes;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final RateLimiter rateLimiter;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetServiceImpl(
      UserRepository users,
      PasswordResetCodeRepository codes,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      RateLimiter rateLimiter,
      Clock clock) {
    this.users = users;
    this.codes = codes;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  @Override
  public boolean mayRequest(String clientAddress) {
    return rateLimiter.tryAcquire("password-reset", clientAddress, 20, Duration.ofHours(1));
  }

  @Override
  public ResetRecipient recipient(String login) {
    String value = login.strip().toLowerCase(Locale.ROOT);
    if (value.contains("@")) {
      Optional<User> owner = users.findByEmail(value);
      if (owner.isPresent()) {
        return new ResetRecipient(ResetRecipient.Status.FOUND, owner.get());
      }
      return new ResetRecipient(
          users.isPendingEmail(value)
              ? ResetRecipient.Status.EMAIL_NOT_CONFIRMED
              : ResetRecipient.Status.NO_ACCOUNT,
          null);
    }
    return users
        .findByUsername(Usernames.normalize(value))
        .map(
            user -> {
              if (user.email() != null) {
                return new ResetRecipient(ResetRecipient.Status.FOUND, user);
              }
              return new ResetRecipient(
                  user.pendingEmail() != null
                      ? ResetRecipient.Status.EMAIL_NOT_CONFIRMED
                      : ResetRecipient.Status.NO_EMAIL,
                  null);
            })
        .orElse(new ResetRecipient(ResetRecipient.Status.NO_ACCOUNT, null));
  }

  /** 32 random bytes for the link; only their SHA-256 is stored. */
  @Override
  public String newCode(UUID userId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    codes.save(
        hash(code), new PasswordResetCode(userId, clock.instant().plus(LINK_LIFETIME)), KEPT_FOR);
    return code;
  }

  @Override
  public ResetLink check(String code) {
    return linkOf(hash(code));
  }

  @Override
  public ResetLink reset(String code, String password) {
    String codeHash = hash(code);
    ResetLink link = linkOf(codeHash);
    if (link.status() != ResetLink.Status.VALID) {
      return link;
    }
    if (!codes.markUsed(codeHash, KEPT_FOR)) {
      return new ResetLink(ResetLink.Status.USED, null);
    }
    users.setPasswordHash(link.user().id(), passwordEncoder.encode(password));
    refreshTokens.revokeAllOfUser(link.user().id(), clock.instant());
    return link;
  }

  private ResetLink linkOf(String codeHash) {
    Optional<PasswordResetCode> stored = codes.find(codeHash);
    if (stored.isEmpty()) {
      return new ResetLink(ResetLink.Status.INVALID, null);
    }
    if (codes.isUsed(codeHash)) {
      return new ResetLink(ResetLink.Status.USED, null);
    }
    if (!clock.instant().isBefore(stored.get().expiresAt())) {
      return new ResetLink(ResetLink.Status.EXPIRED, null);
    }
    return users
        .findById(stored.get().userId())
        .map(user -> new ResetLink(ResetLink.Status.VALID, user))
        .orElse(new ResetLink(ResetLink.Status.INVALID, null));
  }

  private static String hash(String code) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(code.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JVM has SHA-256", e);
    }
  }
}
