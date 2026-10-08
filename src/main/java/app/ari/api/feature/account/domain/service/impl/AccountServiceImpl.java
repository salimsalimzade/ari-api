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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountServiceImpl implements AccountService {

  private static final int DISPLAY_NAME_MAX_LENGTH = 100;

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
  public Optional<User> register(String email, String password, String displayName) {
    Instant now = clock.instant();
    User user =
        new User(
            UUID.randomUUID(),
            normalizeEmail(email),
            passwordEncoder.encode(password),
            displayName.strip(),
            Plan.FREE,
            0,
            null,
            now,
            now);
    return users.insert(user) ? Optional.of(user) : Optional.empty();
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
      String subject, String email, boolean emailVerified, String displayName) {
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
              null,
              googleDisplayName(displayName, normalizedEmail),
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

  /** Google's name for the user, or the part of the email before the {@code @}. */
  private static String googleDisplayName(String displayName, String email) {
    String name =
        StringUtils.hasText(displayName)
            ? displayName.strip()
            : email.substring(0, email.indexOf('@'));
    return name.length() > DISPLAY_NAME_MAX_LENGTH
        ? name.substring(0, DISPLAY_NAME_MAX_LENGTH)
        : name;
  }
}
