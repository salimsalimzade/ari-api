package app.ari.api.feature.save.domain.model;

import app.ari.api.common.image.ImageFormat;
import java.util.Optional;

/** The image of an image clip: a JPEG, PNG or WebP of at most 2 MB, checked by its bytes. */
public final class ClipImage {

  public static final int MAX_BYTES = 2 * 1024 * 1024;

  private final byte[] bytes;
  private final ImageFormat format;

  private ClipImage(byte[] bytes, ImageFormat format) {
    this.bytes = bytes;
    this.format = format;
  }

  /** Empty when the bytes are not a supported image or are over the limit. */
  public static Optional<ClipImage> of(byte[] bytes) {
    if (bytes.length > MAX_BYTES) {
      return Optional.empty();
    }
    return ImageFormat.of(bytes).map(format -> new ClipImage(bytes, format));
  }

  public byte[] bytes() {
    return bytes;
  }

  public ImageFormat format() {
    return format;
  }
}
