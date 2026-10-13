package app.ari.api.feature.account.application.exception.toomanyrequests;

import app.ari.api.common.exception.TooManyRequestsException;

/** Over 30 username checks a minute from one address. */
public class UsernameCheckRateLimitedException extends TooManyRequestsException {

  public UsernameCheckRateLimitedException() {
    super("RATE_LIMITED", "Too many username checks. Wait a minute and try again.");
  }
}
