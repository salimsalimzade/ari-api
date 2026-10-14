package app.ari.api.feature.save.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaveCursorTest {

  private static final Instant AT = Instant.parse("2026-03-04T05:06:07.123456Z");

  @Test
  void aCursorSurvivesTheRoundTrip() {
    SaveCursor cursor = new SaveCursor(AT, UUID.randomUUID());

    assertThat(SaveCursor.decode(cursor.encode())).contains(cursor);
    assertThat(cursor.order()).isEqualTo(SaveOrder.NEWEST);
  }

  @Test
  void aPageOrderCursorKeepsItsPage() {
    SaveCursor cursor = new SaveCursor(4, AT, UUID.randomUUID());

    assertThat(SaveCursor.decode(cursor.encode())).contains(cursor);
    assertThat(cursor.order()).isEqualTo(SaveOrder.PAGE);
  }

  @Test
  void aSaveWithoutAPageNumberIsOnPageZeroInThePageOrder() {
    UUID id = UUID.randomUUID();
    Save save =
        new Save(
            id,
            null,
            null,
            SaveKind.HIGHLIGHT,
            null,
            null,
            "t",
            null,
            null,
            "",
            null,
            null,
            AT,
            AT);

    assertThat(SaveCursor.after(save, SaveOrder.PAGE)).isEqualTo(new SaveCursor(0, AT, id));
    assertThat(SaveCursor.after(save, SaveOrder.NEWEST)).isEqualTo(new SaveCursor(AT, id));
  }

  @Test
  void anythingElseIsNotACursor() {
    assertThat(SaveCursor.decode("")).isEmpty();
    assertThat(SaveCursor.decode("not a cursor!")).isEmpty();
    assertThat(SaveCursor.decode("bm8tYmFy")).isEmpty(); // "no-bar"
    assertThat(SaveCursor.decode("eHx5")).isEmpty(); // "x|y"
    String id = UUID.randomUUID().toString();
    assertThat(SaveCursor.decode(encoded(AT + "|" + id + "|x"))).isEmpty();
    assertThat(SaveCursor.decode(encoded(AT + "|" + id + "|-1"))).isEmpty();
    assertThat(SaveCursor.decode(encoded(AT + "|" + id + "|1|2"))).isEmpty();
  }

  private static String encoded(String plain) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }
}
