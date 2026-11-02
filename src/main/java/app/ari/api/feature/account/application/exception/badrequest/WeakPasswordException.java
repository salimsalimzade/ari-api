package app.ari.api.feature.account.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

/** Supabase Auth's password rules refused the password, though it passed ours. */
public class WeakPasswordException extends BadRequestException {

  public WeakPasswordException() {
    super("WEAK_PASSWORD", "Choose a stronger password");
  }
}
