package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.PasswordResetCode;
import java.time.Duration;
import java.util.Optional;

/**
 * Stores reset links by the hash of their code, never the code itself, for {@code keptFor}: longer
 * than a link works, so an expired or used link can say so.
 */
public interface PasswordResetCodeRepository {

  void save(String codeHash, PasswordResetCode code, Duration keptFor);

  Optional<PasswordResetCode> find(String codeHash);

  boolean isUsed(String codeHash);

  /** Marks the link used; false when it already was, so it works once even when sent twice. */
  boolean markUsed(String codeHash, Duration keptFor);
}
