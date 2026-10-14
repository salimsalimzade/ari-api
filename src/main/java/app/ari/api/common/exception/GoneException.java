package app.ari.api.common.exception;

/** Base class for "this was here, but is no longer usable" errors; mapped to 410. */
public class GoneException extends RuntimeException {

  private final String code;

  public GoneException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code CODE_EXPIRED}. */
  public String getCode() {
    return code;
  }
}
