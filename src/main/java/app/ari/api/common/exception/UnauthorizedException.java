package app.ari.api.common.exception;

/** Base class for "you are not signed in, or the sign-in failed" errors; mapped to 401. */
public class UnauthorizedException extends RuntimeException {

  private final String code;

  public UnauthorizedException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code INVALID_CREDENTIALS}. */
  public String getCode() {
    return code;
  }
}
