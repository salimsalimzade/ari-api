package app.ari.api.feature.fileimport.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class NoSpaceLeftException extends BadRequestException {

  public NoSpaceLeftException() {
    super("NO_SPACE_LEFT", "The file doesn't fit in the upload space that is left");
  }
}
