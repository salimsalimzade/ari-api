package app.ari.api.feature.note.domain.repository;

import app.ari.api.feature.note.domain.model.Note;
import app.ari.api.feature.note.domain.model.NoteBlock;
import app.ari.api.feature.note.domain.model.NoteQuote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores notes and their blocks. Every query is limited to one user's notes. */
public interface NoteRepository {

  void insert(Note note);

  /** Saves the title and the update time. */
  void update(Note note);

  /** Saves the note's paragraphs joined, which search looks through with the title. */
  void setBody(UUID id, String body);

  /** Empty when there is no such note, or it is someone else's. */
  Optional<Note> findById(UUID userId, UUID id);

  /**
   * The user's notes, the one changed most recently first. With {@code prefixQuery} (a {@code
   * to_tsquery} text), only those whose title and paragraphs have words it starts, or whose text
   * {@code containsPattern} (a {@code LIKE} pattern) matches.
   */
  List<Note> findAll(UUID userId, String prefixQuery, String containsPattern);

  /** The note's blocks, in order. */
  List<NoteBlock> findBlocks(UUID noteId);

  /** Adds the blocks at positions 0, 1, 2 and so on; there must be at least one. */
  void insertBlocks(UUID noteId, List<NoteBlock> blocks);

  void deleteBlocks(UUID noteId);

  /** Every quote in the user's notes, by note and in order within it. */
  List<NoteQuote> findQuotes(UUID userId);

  /** The user's notes with a paragraph that has more than whitespace in it. */
  List<UUID> findWritten(UUID userId);

  /** False when there was nothing to delete. Its blocks go with it. */
  boolean delete(UUID userId, UUID id);
}
