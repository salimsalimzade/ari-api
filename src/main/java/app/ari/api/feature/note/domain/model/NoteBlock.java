package app.ari.api.feature.note.domain.model;

import java.util.UUID;

/**
 * One block of a note: a TEXT block has {@code text} (empty for a blank paragraph) and no save, a
 * QUOTE block has {@code saveId} and no text.
 */
public record NoteBlock(NoteBlockKind kind, String text, UUID saveId) {}
