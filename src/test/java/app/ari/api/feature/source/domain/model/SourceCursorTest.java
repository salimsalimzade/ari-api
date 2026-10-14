package app.ari.api.feature.source.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceCursorTest {

  @Test
  void aCursorSurvivesTheRoundTrip() {
    SourceCursor cursor =
        new SourceCursor(Instant.parse("2026-03-04T05:06:07.123456Z"), UUID.randomUUID());

    assertThat(SourceCursor.decode(cursor.encode())).contains(cursor);
  }

  @Test
  void anythingElseIsNotACursor() {
    assertThat(SourceCursor.decode("")).isEmpty();
    assertThat(SourceCursor.decode("not a cursor!")).isEmpty();
    assertThat(SourceCursor.decode("bm8tYmFy")).isEmpty(); // "no-bar"
    assertThat(SourceCursor.decode("eHx5")).isEmpty(); // "x|y"
  }
}
