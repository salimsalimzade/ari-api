package app.ari.api.feature.fileimport.domain.port;

import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FoundFile;

/** Finds the marks in one kind of file; an adapter in infrastructure/ for each kind. */
public interface MarkReader {

  FileKind kind();

  /**
   * The file's title, author and marks in order. Throws {@code FileReadException} when the file can
   * never be read; any other exception is worth a retry.
   */
  FoundFile read(byte[] content);
}
