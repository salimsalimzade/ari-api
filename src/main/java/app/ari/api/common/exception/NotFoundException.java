package app.ari.api.common.exception;

/** Base class for "this resource does not exist" errors; mapped to 404. */
public class NotFoundException extends RuntimeException {

  private final String code;

  /** With the general code {@code NOT_FOUND}. */
  public NotFoundException(String message) {
    this("NOT_FOUND", message);
  }

  public NotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code ACCOUNT_NOT_FOUND}. */
  public String getCode() {
    return code;
  }
}
