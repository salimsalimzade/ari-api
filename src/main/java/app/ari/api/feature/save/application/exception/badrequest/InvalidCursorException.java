package app.ari.api.feature.save.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class InvalidCursorException extends BadRequestException {

  public InvalidCursorException() {
    super("INVALID_CURSOR", "The cursor is not one this API returned; start from the first page");
  }
}
