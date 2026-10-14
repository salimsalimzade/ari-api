package app.ari.api.feature.save.application.exception.payloadtoolarge;

import app.ari.api.common.exception.PayloadTooLargeException;

public class ClipImageTooLargeException extends PayloadTooLargeException {

  public ClipImageTooLargeException() {
    super("A clipped image can be at most 2 MB");
  }
}
