package app.ari.api.common.exception;

/** Base class for "this resource does not exist" errors; mapped to 404 {@code NOT_FOUND}. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
