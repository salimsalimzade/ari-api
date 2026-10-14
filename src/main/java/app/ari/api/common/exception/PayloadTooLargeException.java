package app.ari.api.common.exception;

/** Base class for "the upload is over its size limit" errors; mapped to 413 FILE_TOO_LARGE. */
public class PayloadTooLargeException extends RuntimeException {

  public PayloadTooLargeException(String message) {
    super(message);
  }

  /** The stable error code the frontend checks. */
  public String getCode() {
    return "FILE_TOO_LARGE";
  }
}
