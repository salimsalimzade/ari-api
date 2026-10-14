package app.ari.api.feature.fileimport.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MappedColorTest {

  @Test
  void eachColorGoesToTheNearestOfTheFive() {
    assertThat(MappedColor.ofRgb(1, 1, 0)).isEqualTo(new MappedColor(MarkColor.YELLOW, null));
    assertThat(MappedColor.ofRgb(1, 0.82f, 0)).isEqualTo(new MappedColor(MarkColor.YELLOW, null));
    assertThat(MappedColor.ofRgb(0.49f, 0.98f, 0.43f).color()).isEqualTo(MarkColor.GREEN);
    assertThat(MappedColor.ofRgb(0, 1, 1).color()).isEqualTo(MarkColor.BLUE);
    assertThat(MappedColor.ofRgb(0.41f, 0.69f, 1).color()).isEqualTo(MarkColor.BLUE);
    assertThat(MappedColor.ofRgb(1, 0.4f, 0.7f).color()).isEqualTo(MarkColor.PINK);
    assertThat(MappedColor.ofRgb(0.78f, 0.5f, 1).color()).isEqualTo(MarkColor.PURPLE);
  }

  @Test
  void orangeAndRedKeepTheFilesColor() {
    assertThat(MappedColor.ofRgb(1, 0.5f, 0))
        .isEqualTo(new MappedColor(MarkColor.YELLOW, FileColor.ORANGE));
    assertThat(MappedColor.ofHex("#FF0000"))
        .isEqualTo(new MappedColor(MarkColor.PINK, FileColor.RED));
  }

  @Test
  void graysTellNothingAndBecomeYellow() {
    assertThat(MappedColor.ofRgb(0.5f, 0.5f, 0.5f)).isEqualTo(MappedColor.DEFAULT);
    assertThat(MappedColor.ofHex("not a color")).isEqualTo(MappedColor.DEFAULT);
    assertThat(MappedColor.hasHue("D9D9D9")).isFalse();
    assertThat(MappedColor.hasHue("auto")).isFalse();
    assertThat(MappedColor.hasHue("FFFF00")).isTrue();
  }
}
