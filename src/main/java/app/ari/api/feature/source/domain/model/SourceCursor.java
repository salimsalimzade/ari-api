package app.ari.api.feature.source.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The place on the Sources page a page ends at: the last source's latest save time and its id, the
 * one saved from most recently first.
 */
public record SourceCursor(Instant lastSavedAt, UUID id) {

  /** Opaque to clients: base64url of "time|id". */
  public String encode() {
    String plain = lastSavedAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /** Empty when {@code cursor} was not made by {@link #encode()}. */
  public static Optional<SourceCursor> decode(String cursor) {
    try {
      String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int bar = plain.indexOf('|');
      if (bar < 0) {
        return Optional.empty();
      }
      return Optional.of(
          new SourceCursor(
              Instant.parse(plain.substring(0, bar)), UUID.fromString(plain.substring(bar + 1))));
    } catch (IllegalArgumentException | DateTimeException e) {
      return Optional.empty();
    }
  }
}
