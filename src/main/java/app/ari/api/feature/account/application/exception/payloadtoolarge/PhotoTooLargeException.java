package app.ari.api.feature.account.application.exception.payloadtoolarge;

import app.ari.api.common.exception.PayloadTooLargeException;

public class PhotoTooLargeException extends PayloadTooLargeException {

  public PhotoTooLargeException() {
    super("A profile photo can be at most 2 MB");
  }
}
