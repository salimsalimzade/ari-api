package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/** The verify link is unknown, expired or used, or a newer address is waiting for its link. */
public class InvalidEmailCodeException extends UnauthorizedException {

  public InvalidEmailCodeException() {
    super("INVALID_CODE", "This link has expired or was already used. Ask for a new one.");
  }
}
