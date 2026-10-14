package app.ari.api.feature.fileimport.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class UnsupportedFileException extends BadRequestException {

  public UnsupportedFileException() {
    super("UNSUPPORTED_FILE", "Ari imports PDF and Word (DOCX) files");
  }
}
