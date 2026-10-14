package app.ari.api.feature.fileimport.domain.repository;

import app.ari.api.feature.fileimport.domain.model.FileImport;
import app.ari.api.feature.fileimport.domain.model.ImportMark;
import app.ari.api.feature.fileimport.domain.model.ImportStatus;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores imports and their marks. Every query but the consumer's is limited to one user's. */
public interface ImportRepository {

  /**
   * Locks the user's row (the account feature's; only locked here) until the transaction ends, so
   * two uploads at once can't both take the last of the space. Returns the user's id.
   */
  UUID lockUser(UUID userId);

  /** The size of the user's files that take upload space (see {@link ImportStatus#takesSpace}). */
  long usedBytes(UUID userId);

  void insert(FileImport fileImport);

  /** Empty when there is no such import, or it is someone else's. */
  Optional<FileImport> findById(UUID userId, UUID id);

  /** Any user's import, for the consumer that reads it. */
  Optional<FileImport> findForReading(UUID id);

  /** The user's imports that aren't dismissed, newest first, at most {@code limit}. */
  List<FileImport> findAll(UUID userId, int limit);

  /** QUEUED, or READING again after a crash, becomes READING; false for any other status. */
  boolean startReading(UUID id, Instant at);

  /** READING becomes READY (or EMPTY), with its source and the time; the file is gone. */
  void finishReading(UUID id, UUID sourceId, ImportStatus status, Instant at);

  /** Reading failed for good; the file stays for Retry. */
  void fail(UUID id, String errorCode, Instant at);

  /** FAILED becomes QUEUED, with one more attempt; false for any other status. */
  boolean requeue(UUID userId, UUID id, Instant at);

  void setStatus(UUID id, ImportStatus status, Instant at);

  /** SAVED, with the first review's time kept. */
  void markSaved(UUID id, Instant at);

  /** Hidden from the Imports page; its file is gone. */
  void dismiss(UUID id, Instant at);

  void insertMarks(List<ImportMark> marks);

  /** In the file's order. */
  List<ImportMark> findMarks(UUID importId);

  void updateMark(UUID id, UUID saveId, MarkColor color, boolean selected);

  void deleteMarks(UUID importId);
}
