package app.ari.api.feature.save.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** The place in the Library a page ends at: the last save's time and id, newest first. */
public record SaveCursor(Instant createdAt, UUID id) {

  /** Opaque to clients: base64url of "time|id". */
  public String encode() {
    String plain = createdAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /** Empty when {@code cursor} was not made by {@link #encode()}. */
  public static Optional<SaveCursor> decode(String cursor) {
    try {
      String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int bar = plain.indexOf('|');
      if (bar < 0) {
        return Optional.empty();
      }
      return Optional.of(
          new SaveCursor(
              Instant.parse(plain.substring(0, bar)), UUID.fromString(plain.substring(bar + 1))));
    } catch (IllegalArgumentException | DateTimeException e) {
      return Optional.empty();
    }
  }
}
