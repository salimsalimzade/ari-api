package app.ari.api.feature.save.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The place in a list of saves a page ends at: the last save's time and id, and in the {@link
 * SaveOrder#PAGE} order also its page number (0 for a save without one).
 */
public record SaveCursor(Integer pageNumber, Instant createdAt, UUID id) {

  /** A place in the newest-first order. */
  public SaveCursor(Instant createdAt, UUID id) {
    this(null, createdAt, id);
  }

  /** The place right after {@code save} in {@code order}. */
  public static SaveCursor after(Save save, SaveOrder order) {
    if (order == SaveOrder.NEWEST) {
      return new SaveCursor(save.createdAt(), save.id());
    }
    int page = save.pageNumber() == null ? 0 : save.pageNumber();
    return new SaveCursor(page, save.createdAt(), save.id());
  }

  /** The order this cursor goes on in. */
  public SaveOrder order() {
    return pageNumber == null ? SaveOrder.NEWEST : SaveOrder.PAGE;
  }

  /** Opaque to clients: base64url of "time|id", or "time|id|page" in the page order. */
  public String encode() {
    String plain = createdAt + "|" + id + (pageNumber == null ? "" : "|" + pageNumber);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /** Empty when {@code cursor} was not made by {@link #encode()}. */
  public static Optional<SaveCursor> decode(String cursor) {
    try {
      String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = plain.split("\\|", -1);
      if (parts.length < 2 || parts.length > 3) {
        return Optional.empty();
      }
      Integer page = parts.length == 3 ? Integer.valueOf(parts[2]) : null;
      if (page != null && page < 0) {
        return Optional.empty();
      }
      return Optional.of(new SaveCursor(page, Instant.parse(parts[0]), UUID.fromString(parts[1])));
    } catch (IllegalArgumentException | DateTimeException e) {
      return Optional.empty();
    }
  }
}
