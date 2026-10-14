package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/**
 * The extension's session can't continue: its refresh token is gone, expired or revoked, or the
 * user is. Unlike {@link SessionExpiredException} it clears no cookies: the website's session is
 * another one.
 */
public class ExtensionSessionExpiredException extends UnauthorizedException {

  public ExtensionSessionExpiredException() {
    super("UNAUTHORIZED", "The extension's session has ended. Sign in again.");
  }
}
