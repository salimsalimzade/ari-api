package app.ari.api.feature.account.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class NoPendingEmailException extends BadRequestException {

  public NoPendingEmailException() {
    super("No email is waiting for its link");
  }
}
