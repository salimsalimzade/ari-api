package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.feature.account.domain.model.Plan;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  /**
   * Checked when a user has no password, so unknown usernames answer as slowly as wrong passwords.
   */
  private final String dummyPasswordHash;

  public AccountServiceImpl(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public Optional<User> register(
      String username, String password, String fullName, LocalDate birthDate) {
    Instant now = clock.instant();
    User user =
        new User(
            UUID.randomUUID(),
            null,
            null,
            Usernames.normalize(username),
            passwordEncoder.encode(password),
            fullName.strip(),
            birthDate,
            null,
            Plan.NONE,
            0,
            null,
            now,
            now);
    return users.insert(user) ? Optional.of(user) : Optional.empty();
  }

  @Override
  public void changeSavesCount(UUID id, int delta) {
    users.changeSavesCount(id, delta);
  }

  @Override
  public Optional<User> authenticate(String username, String password) {
    Optional<User> user = users.findByUsername(Usernames.normalize(username));
    String hash = user.map(User::passwordHash).orElse(null);
    boolean matches = passwordEncoder.matches(password, hash != null ? hash : dummyPasswordHash);
    return matches && hash != null ? user : Optional.empty();
  }

  @Override
  public Optional<User> findById(UUID id) {
    return users.findById(id);
  }

  @Override
  public Optional<User> updateProfile(
      UUID id, String username, String fullName, LocalDate birthDate) {
    String normalized = Usernames.normalize(username);
    if (users.findByUsername(normalized).filter(owner -> !owner.id().equals(id)).isPresent()) {
      return Optional.empty();
    }
    try {
      users.updateProfile(id, normalized, fullName.strip(), birthDate, clock.instant());
    } catch (DuplicateKeyException e) {
      return Optional.empty(); // another account took it a moment ago
    }
    return users.findById(id);
  }
}
