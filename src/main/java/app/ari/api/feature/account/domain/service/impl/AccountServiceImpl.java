package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.feature.account.domain.model.AuthProvider;
import app.ari.api.feature.account.domain.model.OAuthAccount;
import app.ari.api.feature.account.domain.model.Plan;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.repository.OAuthAccountRepository;
import app.ari.api.feature.account.domain.repository.RefreshTokenRepository;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountServiceImpl implements AccountService {

  private static final int FULL_NAME_MAX_LENGTH = 150;
  private static final int USERNAME_MIN_LENGTH = 3;
  private static final int USERNAME_MAX_LENGTH = 30;
  private static final Pattern USERNAME_INVALID_CHARACTERS = Pattern.compile("[^a-z0-9_.]");

  private final UserRepository users;
  private final OAuthAccountRepository oauthAccounts;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  /** Checked when a user has no password, so unknown emails answer as slowly as wrong passwords. */
  private final String dummyPasswordHash;

  public AccountServiceImpl(
      UserRepository users,
      OAuthAccountRepository oauthAccounts,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.oauthAccounts = oauthAccounts;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public Optional<User> register(
      String email, String username, String password, String fullName, LocalDate birthDate) {
    Instant now = clock.instant();
    User user =
        new User(
            UUID.randomUUID(),
            normalizeEmail(email),
            normalizeUsername(username),
            passwordEncoder.encode(password),
            fullName.strip(),
            birthDate,
            Plan.FREE,
            0,
            null,
            now,
            now);
    return users.insert(user) ? Optional.of(user) : Optional.empty();
  }

  @Override
  public boolean usernameTaken(String username) {
    return users.existsByUsername(normalizeUsername(username));
  }

  @Override
  public Optional<User> authenticate(String email, String password) {
    Optional<User> user = users.findByEmail(normalizeEmail(email));
    String hash = user.map(User::passwordHash).orElse(null);
    boolean matches = passwordEncoder.matches(password, hash != null ? hash : dummyPasswordHash);
    return matches && hash != null ? user : Optional.empty();
  }

  @Override
  public Optional<User> findById(UUID id) {
    return users.findById(id);
  }

  @Override
  public Optional<User> signInWithGoogle(
      String subject, String email, boolean emailVerified, String fullName) {
    Optional<OAuthAccount> linked =
        oauthAccounts.findByProviderUserId(AuthProvider.GOOGLE, subject);
    if (linked.isPresent()) {
      return users.findById(linked.get().userId());
    }
    if (!emailVerified || !StringUtils.hasText(email)) {
      return Optional.empty();
    }

    String normalizedEmail = normalizeEmail(email);
    Instant now = clock.instant();
    Optional<User> existing = users.findByEmail(normalizedEmail);
    User user;
    if (existing.isPresent()) {
      user = existing.get();
      if (user.emailVerifiedAt() == null) {
        // Whoever registered this email never proved they own it; Google just did.
        user = user.verifiedByGoogle(now);
        users.update(user);
        refreshTokens.revokeAllForUser(user.id(), now);
      }
    } else {
      user =
          new User(
              UUID.randomUUID(),
              normalizedEmail,
              generateUsername(normalizedEmail),
              null,
              googleFullName(fullName, normalizedEmail),
              null,
              Plan.FREE,
              0,
              now,
              now,
              now);
      if (!users.insert(user)) {
        return Optional.empty();
      }
    }

    OAuthAccount account =
        new OAuthAccount(
            UUID.randomUUID(), user.id(), AuthProvider.GOOGLE, subject, normalizedEmail, now);
    return oauthAccounts.insert(account) ? Optional.of(user) : Optional.empty();
  }

  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static String normalizeUsername(String username) {
    return username.strip().toLowerCase(Locale.ROOT);
  }

  /** Google's name for the user, or the part of the email before the {@code @}. */
  private static String googleFullName(String fullName, String email) {
    String name =
        StringUtils.hasText(fullName) ? fullName.strip() : email.substring(0, email.indexOf('@'));
    return name.length() > FULL_NAME_MAX_LENGTH ? name.substring(0, FULL_NAME_MAX_LENGTH) : name;
  }

  /**
   * A free username for a new Google user: the email's part before the {@code @}, cleaned to the
   * allowed characters, then {@code 2}, {@code 3}, ... when taken, and a random number as a last
   * resort. The unique key still decides if two sign-ups pick the same one at once.
   */
  private String generateUsername(String email) {
    int at = email.indexOf('@');
    String local =
        USERNAME_INVALID_CHARACTERS
            .matcher(at > 0 ? email.substring(0, at) : email)
            .replaceAll("_");
    String base =
        (local + "_".repeat(USERNAME_MIN_LENGTH))
            .substring(0, Math.clamp(local.length(), USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
    if (!users.existsByUsername(base)) {
      return base;
    }
    for (int n = 2; n <= 10; n++) {
      String candidate = withSuffix(base, String.valueOf(n));
      if (!users.existsByUsername(candidate)) {
        return candidate;
      }
    }
    return withSuffix(base, "%04d".formatted(ThreadLocalRandom.current().nextInt(10_000)));
  }

  private static String withSuffix(String base, String suffix) {
    int keep = Math.min(base.length(), USERNAME_MAX_LENGTH - suffix.length());
    return base.substring(0, keep) + suffix;
  }
}
