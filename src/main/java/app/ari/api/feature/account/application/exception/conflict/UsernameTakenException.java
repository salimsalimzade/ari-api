package app.ari.api.feature.account.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

public class UsernameTakenException extends ConflictException {

  public UsernameTakenException() {
    super("USERNAME_TAKEN", "This username is already taken");
  }
}
