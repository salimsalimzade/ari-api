package app.ari.api.common.exception;

/** Base class for "the request breaks a rule" errors; mapped to 400 {@code BAD_REQUEST}. */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
