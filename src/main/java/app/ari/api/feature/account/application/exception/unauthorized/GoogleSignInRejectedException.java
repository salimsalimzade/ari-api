package app.ari.api.feature.account.application.exception.unauthorized;

import app.ari.api.common.exception.UnauthorizedException;

/**
 * Google sign-in refused: Google hasn't verified the email, or the user already has another Google
 * account. The sign-in handler turns it into a redirect to {@code /login?error=google}.
 */
public class GoogleSignInRejectedException extends UnauthorizedException {

  public GoogleSignInRejectedException() {
    super("UNAUTHORIZED", "Google sign-in was refused");
  }
}
