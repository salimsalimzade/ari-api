package app.ari.api.feature.fileimport.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** The files Ari reads: PDF, and Word's DOCX (not the old DOC), recognized by their bytes. */
public enum FileKind {
  PDF("application/pdf"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private static final byte[] PDF_START = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] ZIP_START = {'P', 'K', 3, 4};
  // A DOCX is a ZIP whose parts include the Word document.
  private static final byte[] WORD_PART = "word/".getBytes(StandardCharsets.US_ASCII);

  private final String contentType;

  FileKind(String contentType) {
    this.contentType = contentType;
  }

  public String contentType() {
    return contentType;
  }

  /** Empty for any other file, whatever its name says. */
  public static Optional<FileKind> of(byte[] content) {
    if (startsWith(content, PDF_START)) {
      return Optional.of(PDF);
    }
    if (startsWith(content, ZIP_START) && contains(content, WORD_PART)) {
      return Optional.of(DOCX);
    }
    return Optional.empty();
  }

  public static Optional<FileKind> ofContentType(String contentType) {
    return Arrays.stream(values()).filter(kind -> kind.contentType.equals(contentType)).findFirst();
  }

  private static boolean startsWith(byte[] content, byte[] start) {
    return content.length >= start.length
        && Arrays.equals(content, 0, start.length, start, 0, start.length);
  }

  private static boolean contains(byte[] content, byte[] part) {
    outer:
    for (int i = 0; i <= content.length - part.length; i++) {
      for (int j = 0; j < part.length; j++) {
        if (content[i + j] != part[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }
}
