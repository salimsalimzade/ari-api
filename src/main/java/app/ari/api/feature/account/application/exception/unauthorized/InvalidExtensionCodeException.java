package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/** The extension's sign-in code is unknown, expired or used, or its user is gone. */
public class InvalidExtensionCodeException extends UnauthorizedException {

  public InvalidExtensionCodeException() {
    super("INVALID_CODE", "This sign-in code has expired or was already used.");
  }
}
