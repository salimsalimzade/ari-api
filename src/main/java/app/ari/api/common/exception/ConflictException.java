package app.ari.api.common.exception;

/** Base class for "this clashes with existing data" errors; mapped to 409. */
public class ConflictException extends RuntimeException {

  private final String code;

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code EMAIL_TAKEN}. */
  public String getCode() {
    return code;
  }
}
