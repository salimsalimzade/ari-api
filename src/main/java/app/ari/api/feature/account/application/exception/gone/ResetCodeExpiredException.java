package app.ari.api.feature.account.application.exception.gone;

import app.ari.api.common.exception.GoneException;

public class ResetCodeExpiredException extends GoneException {

  public ResetCodeExpiredException() {
    super("CODE_EXPIRED", "This link has expired. Ask for a new one.");
  }
}
