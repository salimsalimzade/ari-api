package app.ari.api.feature.fileimport.domain.service;

import app.ari.api.feature.fileimport.domain.model.FileImport;
import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.ImportMark;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import app.ari.api.feature.fileimport.domain.model.UploadSpace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Imports: the uploaded file in storage until it is read, the marks reading finds, and what the
 * review keeps. Files and pictures are stored outside the database, so each change says when they
 * are written and deleted.
 */
public interface ImportService {

  UploadSpace space(UUID userId);

  /** The upload space, with the user locked until the transaction ends. */
  UploadSpace lockSpace(UUID userId);

  /**
   * Stores the file and adds its import, QUEUED. The file is deleted again if the transaction rolls
   * back.
   */
  FileImport create(UUID userId, String fileName, FileKind kind, byte[] content);

  /**
   * Adds the import of a photo the browser read already, READING: nothing is stored, and {@link
   * #finishReading} keeps its marks next.
   */
  FileImport createRead(UUID userId, String fileName, String contentType, long sizeBytes);

  Optional<FileImport> find(UUID userId, UUID id);

  /** The user's imports that aren't dismissed, newest first. */
  List<FileImport> list(UUID userId);

  /** In the file's order. */
  List<ImportMark> marks(UUID importId);

  /** For the consumer: the import, now READING; empty when it is gone or not waiting to be read. */
  Optional<FileImport> startReading(UUID id);

  /**
   * Reads the stored file. Throws {@code FileReadException} when it can never be read; anything
   * else is worth a retry.
   */
  FoundFile read(FileImport fileImport);

  /**
   * Keeps what reading found: the marks with their pictures (stored now, deleted again if the
   * transaction rolls back) and the file's source, READY; or EMPTY without marks. The file is
   * deleted once the transaction commits.
   */
  void finishReading(FileImport fileImport, UUID sourceId, List<FoundMark> marks);

  /** Reading failed for good; the file stays, for Retry. */
  void fail(UUID id, ReadFailure failure);

  /** FAILED, with its file, goes back in line; false otherwise. */
  boolean requeue(FileImport fileImport);

  /** Drops what reading found: the marks and their pictures (once the transaction commits). */
  void discard(FileImport fileImport, List<ImportMark> marks);

  /** Hides a FAILED or EMPTY import; its file is deleted once the transaction commits. */
  void dismiss(FileImport fileImport);

  /**
   * Saves the review: {@code selected} marks are ticked, {@code newSaves} are the saves just made
   * for some of them (by mark id), unticked ones lost theirs, and {@code color} (null for none) is
   * now every mark's.
   */
  void applyReview(
      FileImport fileImport,
      List<ImportMark> marks,
      Set<UUID> selected,
      Map<UUID, UUID> newSaves,
      MarkColor color);

  /** A link to a mark's picture for an hour; empty when it has none. */
  Optional<String> pictureUrl(ImportMark mark);

  /** The mark's picture, to save a copy; null when it has none. */
  byte[] picture(ImportMark mark);
}
