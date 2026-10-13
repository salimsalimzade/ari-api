package app.ari.api.feature.account.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

public class EmailTakenException extends ConflictException {

  public EmailTakenException() {
    super("EMAIL_TAKEN", "This email is already used by another account");
  }
}
