package app.ari.api.feature.account.application.exception.toomanyrequests;

import app.ari.api.common.exception.TooManyRequestsException;

/** Over 1 verify link a minute or 5 a day. */
public class EmailRateLimitedException extends TooManyRequestsException {

  public EmailRateLimitedException() {
    super("RATE_LIMITED", "Too many emails. Wait a minute and try again.");
  }
}
