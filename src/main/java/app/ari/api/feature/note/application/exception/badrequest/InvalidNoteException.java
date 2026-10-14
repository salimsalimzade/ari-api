package app.ari.api.feature.note.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

/** A block holds what its kind doesn't, e.g. a quote with text. */
public class InvalidNoteException extends BadRequestException {

  public InvalidNoteException(String detail) {
    super("INVALID_NOTE", detail);
  }
}
