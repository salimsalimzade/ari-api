package app.ari.api.feature.fileimport.application.exception.conflict;

import app.ari.api.common.exception.ConflictException;

/** The import isn't where the action needs it, e.g. a review of a file still being read. */
public class ImportStateException extends ConflictException {

  public ImportStateException(String message) {
    super("INVALID_IMPORT_STATE", message);
  }
}
