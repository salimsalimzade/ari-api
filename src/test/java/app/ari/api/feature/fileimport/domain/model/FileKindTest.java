package app.ari.api.feature.fileimport.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.feature.fileimport.ImportFiles;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileKindTest {

  @Test
  void knowsAFileByItsBytes() {
    assertThat(FileKind.of(ImportFiles.annotatedPdf())).contains(FileKind.PDF);
    assertThat(FileKind.of(ImportFiles.annotatedDocx())).contains(FileKind.DOCX);
    assertThat(FileKind.of("just text".getBytes(StandardCharsets.UTF_8))).isEmpty();
    assertThat(FileKind.of(new byte[0])).isEmpty();
  }
}
