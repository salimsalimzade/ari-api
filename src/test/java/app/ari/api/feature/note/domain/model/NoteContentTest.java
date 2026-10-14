package app.ari.api.feature.note.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoteContentTest {

  private static final UUID SAVE = UUID.randomUUID();

  @Test
  void aBlocksKindDecidesWhatItHolds() {
    assertThat(content(new NoteBlock(NoteBlockKind.TEXT, "", null)).violation()).isEmpty();
    assertThat(content(new NoteBlock(NoteBlockKind.QUOTE, null, SAVE)).violation()).isEmpty();
    assertThat(content(new NoteBlock(NoteBlockKind.TEXT, null, null)).violation()).isPresent();
    assertThat(content(new NoteBlock(NoteBlockKind.TEXT, "t", SAVE)).violation()).isPresent();
    assertThat(content(new NoteBlock(NoteBlockKind.QUOTE, null, null)).violation()).isPresent();
    assertThat(content(new NoteBlock(NoteBlockKind.QUOTE, "t", SAVE)).violation()).isPresent();
  }

  @Test
  void theTitleIsTrimmedAndCanBeLeftOut() {
    assertThat(new NoteContent("  Swarm decisions \n", List.of()).title())
        .isEqualTo("Swarm decisions");
    assertThat(new NoteContent(null, null).title()).isEmpty();
    assertThat(new NoteContent(null, null).blocks()).isEmpty();
  }

  @Test
  void keepsOnlyTheQuotesItIsToldTo() {
    UUID other = UUID.randomUUID();
    NoteContent note =
        new NoteContent(
            "t",
            List.of(
                new NoteBlock(NoteBlockKind.QUOTE, null, SAVE),
                new NoteBlock(NoteBlockKind.TEXT, "between", null),
                new NoteBlock(NoteBlockKind.QUOTE, null, other)));

    assertThat(note.saveIds()).containsExactly(SAVE, other);
    assertThat(note.quotingOnly(Set.of(SAVE)).blocks())
        .containsExactly(
            new NoteBlock(NoteBlockKind.QUOTE, null, SAVE),
            new NoteBlock(NoteBlockKind.TEXT, "between", null));
  }

  private static NoteContent content(NoteBlock block) {
    return new NoteContent("", List.of(block));
  }
}
