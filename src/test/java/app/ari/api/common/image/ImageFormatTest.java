package app.ari.api.common.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

  @Test
  void recognizesJpegPngAndWebpByTheirFirstBytes() {
    assertThat(ImageFormat.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
        .contains(ImageFormat.JPEG);
    assertThat(
            ImageFormat.of(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00}))
        .contains(ImageFormat.PNG);
    assertThat(ImageFormat.of("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
        .contains(ImageFormat.WEBP);
  }

  @Test
  void anythingElseIsNoPhoto() {
    assertThat(
            ImageFormat.of(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
    assertThat(ImageFormat.of("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII)))
        .isEmpty();
    assertThat(ImageFormat.of(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    assertThat(ImageFormat.of(new byte[0])).isEmpty();
  }
}
