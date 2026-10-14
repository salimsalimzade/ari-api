package app.ari.api.feature.note.domain.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A note with its blocks, in order. */
public record NoteDocument(Note note, List<NoteBlock> blocks) {

  /** The saves it quotes, in the order they come. */
  public Set<UUID> saveIds() {
    Set<UUID> ids = new LinkedHashSet<>();
    for (NoteBlock block : blocks) {
      if (block.kind() == NoteBlockKind.QUOTE) {
        ids.add(block.saveId());
      }
    }
    return ids;
  }
}
