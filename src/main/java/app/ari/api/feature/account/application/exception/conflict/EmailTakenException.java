package app.ari.api.feature.account.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

public class EmailTakenException extends ConflictException {

  public EmailTakenException() {
    super("EMAIL_TAKEN", "An account with this email already exists");
  }
}
