package app.ari.api.feature.save.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaveCursorTest {

  @Test
  void aCursorSurvivesTheRoundTrip() {
    SaveCursor cursor =
        new SaveCursor(Instant.parse("2026-03-04T05:06:07.123456Z"), UUID.randomUUID());

    assertThat(SaveCursor.decode(cursor.encode())).contains(cursor);
  }

  @Test
  void anythingElseIsNotACursor() {
    assertThat(SaveCursor.decode("")).isEmpty();
    assertThat(SaveCursor.decode("not a cursor!")).isEmpty();
    assertThat(SaveCursor.decode("bm8tYmFy")).isEmpty(); // "no-bar"
    assertThat(SaveCursor.decode("eHx5")).isEmpty(); // "x|y"
  }
}
