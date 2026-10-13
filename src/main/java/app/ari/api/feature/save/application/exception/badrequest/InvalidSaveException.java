package app.ari.api.feature.save.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

/** The save breaks a rule that depends on its kind, e.g. a highlight without a color. */
public class InvalidSaveException extends BadRequestException {

  public InvalidSaveException(String detail) {
    super("INVALID_SAVE", detail);
  }
}
