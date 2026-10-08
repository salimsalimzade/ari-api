package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/** The session can't continue: the refresh token is gone, expired or revoked, or the user is. */
public class SessionExpiredException extends UnauthorizedException {

  public SessionExpiredException() {
    super("UNAUTHORIZED", "Your session has ended. Sign in again.");
  }
}
