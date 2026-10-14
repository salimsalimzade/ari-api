package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/** The reset link is unknown, or so old it was forgotten. */
public class InvalidResetCodeException extends UnauthorizedException {

  public InvalidResetCodeException() {
    super("INVALID_CODE", "This link doesn't work. Ask for a new one.");
  }
}
