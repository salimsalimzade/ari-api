package app.ari.api.feature.fileimport.domain.model;

/**
 * A file that can never be read, whatever the retries: reading it again would fail the same way.
 */
public class FileReadException extends RuntimeException {

  private final ReadFailure failure;

  public FileReadException(ReadFailure failure, String message, Throwable cause) {
    super(message, cause);
    this.failure = failure;
  }

  public ReadFailure failure() {
    return failure;
  }
}
