package app.ari.api.feature.account.domain.repository;

import app.ari.api.feature.account.domain.model.EmailCode;
import java.time.Duration;
import java.util.Optional;

/** Stores verify links by the hash of their code, never the code itself. */
public interface EmailCodeRepository {

  void save(String codeHash, EmailCode code, Duration lifetime);

  /** Returns the code and deletes it in one step, so a link works once. */
  Optional<EmailCode> take(String codeHash);
}
