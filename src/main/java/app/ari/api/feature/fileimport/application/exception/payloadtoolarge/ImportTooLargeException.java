package app.ari.api.feature.fileimport.application.exception.payloadtoolarge;

import app.ari.api.common.exception.PayloadTooLargeException;

public class ImportTooLargeException extends PayloadTooLargeException {

  public ImportTooLargeException() {
    super("A file can be at most 50 MB, the whole upload space");
  }
}
