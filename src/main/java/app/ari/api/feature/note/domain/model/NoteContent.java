package app.ari.api.feature.note.domain.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What a client sends for a note: its title, trimmed, and every block in order. */
public record NoteContent(String title, List<NoteBlock> blocks) {

  public NoteContent {
    title = title == null ? "" : title.strip();
    blocks = blocks == null ? List.of() : List.copyOf(blocks);
  }

  /** What stops this from being saved, if anything: a block's kind decides what it holds. */
  public Optional<String> violation() {
    for (NoteBlock block : blocks) {
      boolean text = block.kind() == NoteBlockKind.TEXT;
      if (text && (block.text() == null || block.saveId() != null)) {
        return Optional.of("A TEXT block has text (empty for a blank paragraph) and no saveId");
      }
      if (!text && (block.saveId() == null || block.text() != null)) {
        return Optional.of("A QUOTE block has a saveId and no text");
      }
    }
    return Optional.empty();
  }

  /** The saves it quotes. */
  public Set<UUID> saveIds() {
    Set<UUID> ids = new LinkedHashSet<>();
    for (NoteBlock block : blocks) {
      if (block.kind() == NoteBlockKind.QUOTE) {
        ids.add(block.saveId());
      }
    }
    return ids;
  }

  /** The same note without the quotes of saves not in {@code kept}. */
  public NoteContent quotingOnly(Set<UUID> kept) {
    return new NoteContent(
        title,
        blocks.stream()
            .filter(block -> block.kind() == NoteBlockKind.TEXT || kept.contains(block.saveId()))
            .toList());
  }
}
