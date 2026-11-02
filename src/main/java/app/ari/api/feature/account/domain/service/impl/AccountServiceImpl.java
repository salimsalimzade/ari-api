package app.ari.api.feature.account.domain.service.impl;

import app.ari.api.feature.account.domain.model.NoEmail;
import app.ari.api.feature.account.domain.model.Registration;
import app.ari.api.feature.account.domain.model.User;
import app.ari.api.feature.account.domain.model.Usernames;
import app.ari.api.feature.account.domain.port.IdentityProvider;
import app.ari.api.feature.account.domain.repository.UserRepository;
import app.ari.api.feature.account.domain.service.AccountService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {

  private final UserRepository users;
  private final IdentityProvider identity;
  private final Clock clock;

  public AccountServiceImpl(UserRepository users, IdentityProvider identity, Clock clock) {
    this.users = users;
    this.identity = identity;
    this.clock = clock;
  }

  @Override
  public Registration register(String username, String password, String fullName) {
    String normalized = Usernames.normalize(username);
    if (users.usernameExists(normalized)) {
      return new Registration(Registration.Status.USERNAME_TAKEN, null);
    }
    UUID id = UUID.randomUUID();
    Map<String, String> profile = Map.of("username", normalized, "full_name", fullName.strip());
    return switch (identity.createUser(id, NoEmail.of(id), password, profile)) {
      // The trigger made the profile in Supabase's transaction, which has committed.
      case DONE ->
          users
              .findById(id)
              .map(user -> new Registration(Registration.Status.CREATED, user))
              .orElseThrow(() -> new IllegalStateException("No profile for new user " + id));
      // The profile trigger refused the row: another account took the name a moment ago.
      case TAKEN -> new Registration(Registration.Status.USERNAME_TAKEN, null);
      case WEAK_PASSWORD -> new Registration(Registration.Status.WEAK_PASSWORD, null);
    };
  }

  @Override
  public void changeSavesCount(UUID id, int delta) {
    users.changeSavesCount(id, delta);
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
