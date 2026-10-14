package app.ari.api.feature.account.application.exception.notfound;

import app.ari.api.common.exception.NotFoundException;

/** No account has the username or verified email a reset link was asked for. */
public class AccountNotFoundException extends NotFoundException {

  public AccountNotFoundException() {
    super("ACCOUNT_NOT_FOUND", "No account has that username or email");
  }
}
