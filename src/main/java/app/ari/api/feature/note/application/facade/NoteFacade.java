package app.ari.api.feature.note.application.facade;

import app.ari.api.feature.note.application.model.request.ListNotesRequest;
import app.ari.api.feature.note.application.model.request.NoteRequest;
import app.ari.api.feature.note.application.model.response.NoteResponse;
import app.ari.api.feature.note.application.model.response.NoteSummaryResponse;
import java.util.List;
import java.util.UUID;

/** The Notes page's use cases. */
public interface NoteFacade {

  /** The user's notes, the one changed most recently first; only those it finds with a search. */
  List<NoteSummaryResponse> list(UUID userId, ListNotesRequest request);

  /** Throws {@code InvalidNoteException}. */
  NoteResponse create(UUID userId, NoteRequest request);

  /** Throws {@code NoteNotFoundException}. */
  NoteResponse get(UUID userId, UUID id);

  /**
   * Replaces the whole note; throws {@code NoteNotFoundException} or {@code InvalidNoteException}.
   */
  NoteResponse replace(UUID userId, UUID id, NoteRequest request);

  /** Throws {@code NoteNotFoundException}. */
  void delete(UUID userId, UUID id);
}
