package app.ari.api.feature.account.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

/** The account has no email, so there is nowhere to send a reset link. */
public class NoEmailException extends ConflictException {

  public NoEmailException() {
    super("NO_EMAIL", "This account has no email to send a link to");
  }
}
