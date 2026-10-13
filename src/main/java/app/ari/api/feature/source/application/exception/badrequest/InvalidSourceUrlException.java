package app.ari.api.feature.source.application.exception.badrequest;

import app.ari.api.common.exception.BadRequestException;

public class InvalidSourceUrlException extends BadRequestException {

  public InvalidSourceUrlException() {
    super(
        "INVALID_URL",
        "The source's url must be an http or https address of at most 2048 characters");
  }
}
