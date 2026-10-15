package app.ari.api.feature.fileimport.application.facade;

import app.ari.api.feature.fileimport.application.model.request.PhotoImportRequest;
import app.ari.api.feature.fileimport.application.model.request.ReviewRequest;
import app.ari.api.feature.fileimport.application.model.response.ImportResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportsResponse;
import app.ari.api.feature.fileimport.application.model.response.ReviewResponse;
import java.util.UUID;

/** Importing PDF and Word files, and photos read in the browser (docs/sequences.md section 5). */
public interface ImportFacade {

  /** The Imports page: the files that aren't dismissed, newest first, and the upload space. */
  ImportsResponse list(UUID userId);

  /** One import, with its marks once read; throws {@code ImportNotFoundException}. */
  ImportResponse get(UUID userId, UUID id);

  /**
   * Stores the file and queues it to be read: any size that fits in the space left. Throws {@code
   * ImportTooLargeException} (over the whole space), {@code UnsupportedFileException} or {@code
   * NoSpaceLeftException}.
   */
  ImportResponse upload(UUID userId, String fileName, byte[] content);

  /**
   * Keeps the marks the browser found in a photo as an import READY to review, each in its mapped
   * color from a new file source and ticked as a file's would be; EMPTY without any. Throws {@code
   * ImportTooLargeException} or {@code NoSpaceLeftException} for the photo's size, as for a file.
   */
  ImportResponse importPhoto(UUID userId, PhotoImportRequest request);

  /**
   * Saves the ticked marks to the Library and removes the unticked ones that were saved; throws
   * {@code ImportNotFoundException} or {@code ImportStateException} (not read yet).
   */
  ReviewResponse review(UUID userId, UUID id, ReviewRequest request);

  /** Reads a failed file again; throws {@code ImportStateException} for any other. */
  ImportResponse retry(UUID userId, UUID id);

  /** Drops a file ready to review, its marks and its source; nothing reaches the Library. */
  void discard(UUID userId, UUID id);

  /** Hides a failed, empty or discarded file from the Imports page. */
  void dismiss(UUID userId, UUID id);

  /**
   * For the {@code import.process} consumer: reads the import's file and keeps what it finds. A
   * second delivery of the same event does nothing. Throws {@code UnprocessableMessageException}
   * when the file can never be read, and anything else when a retry may help.
   */
  void read(String eventId, UUID importId);

  /** For the consumer, when it stops retrying: the import FAILED, with why. */
  void giveUp(UUID importId, RuntimeException failure);
}
