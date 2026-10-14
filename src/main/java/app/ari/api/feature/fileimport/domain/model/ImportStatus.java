package app.ari.api.feature.fileimport.domain.model;

/**
 * Where an uploaded file is (docs/database.md section 1.4): QUEUED until the consumer takes it,
 * READING while it does, then READY with marks to review, EMPTY without any, or FAILED. Saving the
 * review makes it SAVED; discarding it, DISCARDED.
 */
public enum ImportStatus {
  QUEUED,
  READING,
  READY,
  SAVED,
  EMPTY,
  FAILED,
  DISCARDED;

  /** Whether the file still counts against the upload space. */
  public boolean takesSpace() {
    return this == QUEUED || this == READING || this == READY || this == SAVED;
  }
}
