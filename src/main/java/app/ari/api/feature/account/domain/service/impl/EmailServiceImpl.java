package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.common.ratelimit.RateLimiter;
import app.ari.api.feature.account.domain.model.EmailCode;
import app.ari.api.feature.account.domain.model.EmailConfirmation;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.EmailCodeRepository;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

  private static final Duration LINK_LIFETIME = Duration.ofHours(24);
  private static final String RATE_ACTION = "email-send";

  private final UserRepository users;
  private final EmailCodeRepository codes;
  private final RateLimiter rateLimiter;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public EmailServiceImpl(
      UserRepository users, EmailCodeRepository codes, RateLimiter rateLimiter, Clock clock) {
    this.users = users;
    this.codes = codes;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  @Override
  public boolean mayRequestLink(UUID userId) {
    String subject = userId.toString();
    // The day's counter only moves when the minute allows the link.
    return rateLimiter.tryAcquire(RATE_ACTION + "-minute", subject, 1, Duration.ofMinutes(1))
        && rateLimiter.tryAcquire(RATE_ACTION + "-day", subject, 5, Duration.ofDays(1));
  }

  @Override
  public Optional<String> requestVerification(User user, String email) {
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    Instant now = clock.instant();
    if (normalized.equals(user.email())) {
      users.setPendingEmail(user.id(), null, now);
      return Optional.empty();
    }
    users.setPendingEmail(user.id(), normalized, now);
    return Optional.of(newCode(user.id(), normalized));
  }

  @Override
  public Optional<String> resendVerification(User user) {
    return Optional.ofNullable(user.pendingEmail()).map(email -> newCode(user.id(), email));
  }

  @Override
  public EmailConfirmation confirm(String code) {
    Optional<EmailCode> stored = codes.take(hash(code));
    if (stored.isEmpty()) {
      return EmailConfirmation.INVALID_CODE;
    }
    EmailCode emailCode = stored.get();
    boolean stillPending =
        users
            .findById(emailCode.userId())
            .map(User::pendingEmail)
            .filter(emailCode.email()::equals)
            .isPresent();
    if (!stillPending) {
      return EmailConfirmation.INVALID_CODE;
    }
    if (users.isEmailTaken(emailCode.email(), emailCode.userId())) {
      return EmailConfirmation.EMAIL_TAKEN;
    }
    try {
      return users.confirmEmail(emailCode.userId(), emailCode.email(), clock.instant())
          ? EmailConfirmation.CONFIRMED
          : EmailConfirmation.INVALID_CODE;
    } catch (DuplicateKeyException e) {
      return EmailConfirmation.EMAIL_TAKEN; // another account confirmed it a moment ago
    }
  }

  @Override
  public void removeEmail(UUID userId) {
    users.removeEmail(userId, clock.instant());
  }

  /** 32 random bytes for the link; only their SHA-256 is stored. */
  private String newCode(UUID userId, String email) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    codes.save(hash(code), new EmailCode(userId, email), LINK_LIFETIME);
    return code;
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
