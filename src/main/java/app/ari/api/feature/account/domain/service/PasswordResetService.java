package app.ari.api.feature.account.domain.service;

import app.ari.api.feature.account.domain.model.ResetLink;
import app.ari.api.feature.account.domain.model.ResetRecipient;
import java.util.UUID;

/** A forgotten password: a link to the account's verified email, then a new password. */
public interface PasswordResetService {

  /** Counts one request from the address; false when over 20 an hour. */
  boolean mayRequest(String clientAddress);

  /**
   * The account with this verified email when {@code login} has an "@", else with this username, or
   * why no link can be sent.
   */
  ResetRecipient recipient(String login);

  /** A new link's code for the user, which works once for 30 minutes. */
  String newCode(UUID userId);

  /** What the code stands for now; checking never uses it up. */
  ResetLink check(String code);

  /**
   * Sets the password, uses the code up and ends every session of the user. Returns the link as it
   * was: anything but VALID changed nothing.
   */
  ResetLink reset(String code, String password);
}
