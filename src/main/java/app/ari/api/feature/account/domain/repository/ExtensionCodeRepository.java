package app.ari.api.feature.account.domain.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Stores the extension's sign-in codes by the hash of the code, never the code itself. */
public interface ExtensionCodeRepository {

  void save(String codeHash, UUID userId, Duration lifetime);

  /** Returns the user the code signs in and deletes it in one step, so a code works once. */
  Optional<UUID> take(String codeHash);
}
