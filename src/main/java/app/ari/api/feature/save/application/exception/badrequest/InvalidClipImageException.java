package app.ari.api.feature.save.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class InvalidClipImageException extends BadRequestException {

  public InvalidClipImageException() {
    super("INVALID_IMAGE", "Send a JPEG, PNG or WebP image of at most 2 MB");
  }
}
