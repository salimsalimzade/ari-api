package app.ari.api.feature.note.domain.service;

import app.ari.api.feature.note.domain.model.NoteContent;
import app.ari.api.feature.note.domain.model.NoteDocument;
import app.ari.api.feature.note.domain.model.NoteSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The user's notes: listed, written, rewritten and removed. */
public interface NoteService {

  /**
   * The user's notes, the one changed most recently first, each with the saves it quotes. With a
   * {@code query} (null for none), only those whose title and paragraphs have words its words
   * start, or that have the whole query in them.
   */
  List<NoteSummary> list(UUID userId, String query);

  /** The note with its blocks; empty when there is no such note, or it is someone else's. */
  Optional<NoteDocument> find(UUID userId, UUID id);

  /**
   * Adds a note. The caller has checked {@link NoteContent#violation()} and kept only quotes of the
   * user's own saves.
   */
  NoteDocument create(UUID userId, NoteContent content);

  /**
   * Replaces the note's title and every block, under the same rules as {@link #create}; empty when
   * there is no such note, or it is someone else's.
   */
  Optional<NoteDocument> replace(UUID userId, UUID id, NoteContent content);

  /** False when there was no such note, or it is someone else's. */
  boolean delete(UUID userId, UUID id);
}
