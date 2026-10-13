package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.EmailConfirmation;
import app.ari.api.feature.account.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/**
 * A user's optional email: added or changed through a verify link. The verified email keeps working
 * until the new one is confirmed.
 */
public interface EmailService {

  /** Counts one verify link against the limits; false when over 1 a minute or 5 a day. */
  boolean mayRequestLink(UUID userId);

  /**
   * Makes {@code email} the pending email and returns the code for its link. Empty when it already
   * is the verified email: the pending one is dropped and nothing needs sending.
   */
  Optional<String> requestVerification(User user, String email);

  /** A new code for the pending email; empty when there is none. */
  Optional<String> resendVerification(User user);

  EmailConfirmation confirm(String code);

  void removeEmail(UUID userId);
}
