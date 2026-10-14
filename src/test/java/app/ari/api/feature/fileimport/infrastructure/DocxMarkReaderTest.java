package app.ari.api.feature.fileimport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ari.api.feature.fileimport.ImportFiles;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocxMarkReaderTest {

  private final DocxMarkReader reader = new DocxMarkReader();

  @Test
  void readsHighlightsStrikesAndCommentsWithTheirPages() {
    FoundFile file = reader.read(ImportFiles.annotatedDocx());

    assertThat(file.title()).isEqualTo("Swarm notes");
    assertThat(file.author()).isEqualTo("R. Okafor");
    List<FoundMark> marks = file.marks();
    assertThat(marks)
        .extracting(FoundMark::type)
        .containsExactly(MarkType.HIGHLIGHT, MarkType.STRIKE, MarkType.HIGHLIGHT, MarkType.NOTE);
    assertThat(marks)
        .extracting(FoundMark::text)
        .containsExactly(
            "scouts dance for sites",
            "Bees always choose the closest.",
            "A pink one.",
            "Quorum at fifteen scouts.");
    assertThat(marks).extracting(FoundMark::pageNumber).containsExactly(1, 2, 2, 2);
    assertThat(marks.get(0).color().color()).isEqualTo(MarkColor.YELLOW);
    assertThat(marks.get(2).color().color()).isEqualTo(MarkColor.PINK);
    assertThat(marks.get(3).comment()).isEqualTo("Compare with the 2019 study");
  }

  @Test
  void somethingElseCanNeverBeRead() {
    assertThatThrownBy(() -> reader.read(ImportFiles.brokenPdf()))
        .isInstanceOf(FileReadException.class);
  }
}
