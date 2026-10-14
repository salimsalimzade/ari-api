package app.ari.api.feature.note.domain.model;

import java.util.UUID;

/** A save quoted in a note, for listing the notes with their quotes at once. */
public record NoteQuote(UUID noteId, UUID saveId) {}
