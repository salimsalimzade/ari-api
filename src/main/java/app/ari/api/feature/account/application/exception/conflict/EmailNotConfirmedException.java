package app.ari.api.feature.account.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

/** The email still waits for its verify link; a pending email never gets a reset link. */
public class EmailNotConfirmedException extends ConflictException {

  public EmailNotConfirmedException() {
    super("EMAIL_NOT_CONFIRMED", "Confirm this email with its verify link first");
  }
}
