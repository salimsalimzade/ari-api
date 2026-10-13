package app.ari.api.common.exception;

/** Base class for "slow down, try again later" errors; mapped to 429. */
public class TooManyRequestsException extends RuntimeException {

  private final String code;

  public TooManyRequestsException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The stable error code the frontend checks, e.g. {@code RATE_LIMITED}. */
  public String getCode() {
    return code;
  }
}
