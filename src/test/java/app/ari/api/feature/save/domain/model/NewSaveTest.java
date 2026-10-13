package app.ari.api.feature.save.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NewSaveTest {

  @Test
  void aHighlightNeedsAColorAndNothingElse() {
    assertThat(highlight(HighlightColor.YELLOW).violation(false)).isEmpty();
    assertThat(highlight(null).violation(false)).contains("A highlight needs a color");
    assertThat(highlight(HighlightColor.YELLOW).violation(true)).isPresent();
    assertThat(
            new NewSave(
                    SaveKind.HIGHLIGHT,
                    HighlightColor.BLUE,
                    ClipType.TABLE,
                    "t",
                    null,
                    null,
                    null,
                    null)
                .violation(false))
        .isPresent();
    assertThat(
            new NewSave(
                    SaveKind.HIGHLIGHT, HighlightColor.BLUE, null, "t", "| a |", null, null, null)
                .violation(false))
        .isPresent();
  }

  @Test
  void aTableClipNeedsItsMarkdownAndNoColorOrImage() {
    assertThat(clip(ClipType.TABLE, "| a |").violation(false)).isEmpty();
    assertThat(clip(ClipType.TABLE, null).violation(false)).isPresent();
    assertThat(clip(ClipType.TABLE, "  ").violation(false)).isPresent();
    assertThat(clip(ClipType.TABLE, "| a |").violation(true)).isPresent();
  }

  @Test
  void anImageClipNeedsItsImageAndNoMarkdown() {
    assertThat(clip(ClipType.IMAGE, null).violation(true)).isEmpty();
    assertThat(clip(ClipType.IMAGE, null).violation(false)).isPresent();
    assertThat(clip(ClipType.IMAGE, "| a |").violation(true)).isPresent();
  }

  @Test
  void aClipHasNoColorAndNeedsAClipType() {
    NewSave colored =
        new NewSave(
            SaveKind.CLIP, HighlightColor.GREEN, ClipType.TABLE, "t", "| a |", null, null, null);
    assertThat(colored.violation(false)).contains("A clip has no color");
    NewSave typeless = new NewSave(SaveKind.CLIP, null, null, "t", null, null, null, null);
    assertThat(typeless.violation(false)).isPresent();
  }

  @Test
  void anImportIsNotSavedDirectly() {
    NewSave direct =
        new NewSave(SaveKind.IMPORT, HighlightColor.GREEN, null, "t", null, null, null, null);
    assertThat(direct.violation(false)).isPresent();
  }

  @Test
  void tidiesTheTextCommentSectionAndTags() {
    NewSave save =
        new NewSave(
            SaveKind.HIGHLIGHT,
            HighlightColor.PINK,
            null,
            "  words  ",
            null,
            null,
            "   ",
            List.of(" Swarm ", "swarm", "wild  bees", " "));

    assertThat(save.text()).isEqualTo("words");
    assertThat(save.comment()).isEmpty();
    assertThat(save.section()).isNull();
    assertThat(save.tags()).containsExactly("Swarm", "wild bees");
  }

  @Test
  void anEditToAClipCannotSetAColor() {
    Save clip =
        new Save(
            null,
            null,
            null,
            SaveKind.CLIP,
            null,
            ClipType.TABLE,
            "t",
            "| a |",
            null,
            "",
            null,
            null,
            null,
            null);
    Save highlight =
        new Save(
            null,
            null,
            null,
            SaveKind.HIGHLIGHT,
            HighlightColor.BLUE,
            null,
            "t",
            null,
            null,
            "",
            null,
            null,
            null,
            null);
    SaveChange recolor = new SaveChange(HighlightColor.GREEN, null, null);

    assertThat(recolor.violationFor(clip)).isPresent();
    assertThat(recolor.violationFor(highlight)).isEqualTo(Optional.empty());
    assertThat(new SaveChange(null, " new ", List.of(" a ", "A")).comment()).isEqualTo("new");
    assertThat(new SaveChange(null, " new ", List.of(" a ", "A")).tags()).containsExactly("a");
    assertThat(new SaveChange(null, null, null).tags()).isNull();
  }

  private static NewSave highlight(HighlightColor color) {
    return new NewSave(SaveKind.HIGHLIGHT, color, null, "text", null, null, null, null);
  }

  private static NewSave clip(ClipType type, String markdown) {
    return new NewSave(SaveKind.CLIP, null, type, "caption", markdown, null, null, null);
  }
}
