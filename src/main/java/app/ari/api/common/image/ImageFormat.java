package app.ari.api.common.image;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats Ari stores (profile photos, clipped images), recognized by their first bytes.
 */
public enum ImageFormat {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp");

  private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] WEBP_TAG = "WEBP".getBytes(StandardCharsets.US_ASCII);

  private final String contentType;
  private final String extension;

  ImageFormat(String contentType, String extension) {
    this.contentType = contentType;
    this.extension = extension;
  }

  public String contentType() {
    return contentType;
  }

  public String extension() {
    return extension;
  }

  /** Reads the format from the bytes themselves, never from what the upload claims to be. */
  public static Optional<ImageFormat> of(byte[] bytes) {
    if (startsWith(bytes, 0, JPEG_START)) {
      return Optional.of(JPEG);
    }
    if (startsWith(bytes, 0, PNG_START)) {
      return Optional.of(PNG);
    }
    if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP_TAG)) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
    return bytes.length >= offset + prefix.length
        && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
  }
}
