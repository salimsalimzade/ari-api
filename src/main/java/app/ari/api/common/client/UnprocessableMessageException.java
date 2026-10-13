package app.ari.api.common.client;

/**
 * A message that can never be handled, such as a body that isn't JSON or an unknown type. It goes
 * straight to the dead-letter queue instead of being retried.
 */
public class UnprocessableMessageException extends RuntimeException {

  public UnprocessableMessageException(String message) {
    super(message);
  }

  public UnprocessableMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
