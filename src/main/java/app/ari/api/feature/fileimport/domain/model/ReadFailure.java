package app.ari.api.feature.fileimport.domain.model;

/** Why a file couldn't be read; stored as the import's error code. */
public enum ReadFailure {
  /** Not a PDF or DOCX after all, or damaged. */
  UNREADABLE,
  /** A PDF that needs a password to open. */
  ENCRYPTED,
  /** Reading failed on every retry for another reason. */
  READ_FAILED
}
