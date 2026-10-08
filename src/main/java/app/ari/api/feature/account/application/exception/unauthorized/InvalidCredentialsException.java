package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/**
 * Any failed password sign-in; one message for all cases, so it never reveals which emails exist.
 */
public class InvalidCredentialsException extends UnauthorizedException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Wrong email or password");
  }
}
