package app.ari.api.feature.fileimport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ari.api.feature.fileimport.ImportFiles;
import app.ari.api.feature.fileimport.domain.model.FileColor;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfMarkReaderTest {

  private final PdfMarkReader reader = new PdfMarkReader();

  @Test
  void readsTheMarksWithTheirWordsPagesColorsAndComments() {
    FoundFile file = reader.read(ImportFiles.annotatedPdf());

    assertThat(file.title()).isEqualTo("How Honeybees Choose a New Home");
    assertThat(file.author()).isEqualTo("J. Arden");
    List<FoundMark> marks = file.marks();
    assertThat(marks)
        .extracting(FoundMark::type)
        .containsExactly(
            MarkType.HIGHLIGHT,
            MarkType.STRIKE,
            MarkType.UNDERLINE,
            MarkType.NOTE,
            MarkType.DRAWN,
            MarkType.HANDWRITTEN);
    assertThat(marks).extracting(FoundMark::pageNumber).containsExactly(1, 1, 2, 2, 2, 2);

    FoundMark highlight = marks.get(0);
    assertThat(highlight.text()).isEqualTo("Scouts dance for the best site.");
    assertThat(highlight.color().color()).isEqualTo(MarkColor.GREEN);
    assertThat(highlight.comment()).isEqualTo("Check the 2019 study");
    assertThat(highlight.picture()).isNull();

    assertThat(marks.get(1).text()).isEqualTo("Bees always choose the closest site.");
    FoundMark underline = marks.get(2);
    assertThat(underline.text()).isEqualTo("Piping tells the swarm to warm up.");
    assertThat(underline.color().color()).isEqualTo(MarkColor.PINK);
    assertThat(underline.color().fileColor()).isEqualTo(FileColor.RED);

    FoundMark note = marks.get(3);
    assertThat(note.text()).isEqualTo("Check figure 3 against the photos.");
    assertThat(note.comment()).isNull();

    FoundMark drawn = marks.get(4);
    assertThat(drawn.text()).isEqualTo("The swarm lifts off together.");
    assertThat(drawn.color().color()).isEqualTo(MarkColor.PURPLE);
    assertThat(drawn.picture()).startsWith(0x89, 'P', 'N', 'G');

    FoundMark handwritten = marks.get(5);
    assertThat(handwritten.text()).isEmpty();
    assertThat(handwritten.picture()).startsWith(0x89, 'P', 'N', 'G');
  }

  @Test
  void aFileWithoutMarksHasNone() {
    assertThat(reader.read(ImportFiles.plainPdf()).marks()).isEmpty();
  }

  @Test
  void aDamagedFileCanNeverBeRead() {
    assertThatThrownBy(() -> reader.read(ImportFiles.brokenPdf()))
        .isInstanceOfSatisfying(
            FileReadException.class,
            e -> assertThat(e.failure()).isEqualTo(ReadFailure.UNREADABLE));
  }
}
