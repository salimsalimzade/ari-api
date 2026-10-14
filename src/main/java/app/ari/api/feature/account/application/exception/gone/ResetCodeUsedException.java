package app.ari.api.feature.account.application.exception.gone;

import app.ari.api.common.exception.GoneException;

public class ResetCodeUsedException extends GoneException {

  public ResetCodeUsedException() {
    super("CODE_USED", "This link was already used. Ask for a new one.");
  }
}
