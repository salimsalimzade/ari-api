package app.ari.api.common.exception;

/** Base class for "the request breaks a rule" errors; mapped to 400. */
public class BadRequestException extends RuntimeException {

  private final String code;

  /** With the general code {@code BAD_REQUEST}. */
  public BadRequestException(String message) {
    this("BAD_REQUEST", message);
  }

  public BadRequestException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code INVALID_PHOTO}. */
  public String getCode() {
    return code;
  }
}
