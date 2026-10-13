package app.ari.api.feature.account.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class InvalidPhotoException extends BadRequestException {

  public InvalidPhotoException() {
    super("INVALID_PHOTO", "Choose a JPG, PNG or WebP image of at most 2 MB");
  }
}
