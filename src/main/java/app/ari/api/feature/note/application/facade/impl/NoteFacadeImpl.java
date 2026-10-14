package app.ari.api.feature.note.application.facade.impl;

import app.ari.api.feature.note.application.exception.badrequest.InvalidNoteException;
import app.ari.api.feature.note.application.exception.notfound.NoteNotFoundException;
import app.ari.api.feature.note.application.facade.NoteFacade;
import app.ari.api.feature.note.application.mapper.NoteMapper;
import app.ari.api.feature.note.application.model.request.ListNotesRequest;
import app.ari.api.feature.note.application.model.request.NoteRequest;
import app.ari.api.feature.note.application.model.response.NoteResponse;
import app.ari.api.feature.note.application.model.response.NoteSummaryResponse;
import app.ari.api.feature.note.domain.model.NoteContent;
import app.ari.api.feature.note.domain.model.NoteDocument;
import app.ari.api.feature.note.domain.service.NoteService;
import app.ari.api.feature.save.application.facade.SaveFacade;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quotes go through the Library's facade: a note keeps only quotes of the user's own saves, and
 * each one it answers with carries its save as it is now.
 */
@Service
public class NoteFacadeImpl implements NoteFacade {

  private final NoteService noteService;
  private final SaveFacade saves;
  private final NoteMapper mapper;

  public NoteFacadeImpl(NoteService noteService, SaveFacade saves, NoteMapper mapper) {
    this.noteService = noteService;
    this.saves = saves;
    this.mapper = mapper;
  }

  @Override
  public List<NoteSummaryResponse> list(UUID userId, ListNotesRequest request) {
    return noteService.list(userId, request.q()).stream().map(mapper::toSummaryResponse).toList();
  }

  @Override
  @Transactional
  public NoteResponse create(UUID userId, NoteRequest request) {
    NoteContent content = contentOf(request);
    Map<UUID, SaveResponse> quoted = saves.findAll(userId, content.saveIds());
    NoteDocument created = noteService.create(userId, content.quotingOnly(quoted.keySet()));
    return mapper.toResponse(created, quoted);
  }

  @Override
  public NoteResponse get(UUID userId, UUID id) {
    NoteDocument note = noteService.find(userId, id).orElseThrow(NoteNotFoundException::new);
    return mapper.toResponse(note, saves.findAll(userId, note.saveIds()));
  }

  @Override
  @Transactional
  public NoteResponse replace(UUID userId, UUID id, NoteRequest request) {
    NoteContent content = contentOf(request);
    Map<UUID, SaveResponse> quoted = saves.findAll(userId, content.saveIds());
    NoteDocument replaced =
        noteService
            .replace(userId, id, content.quotingOnly(quoted.keySet()))
            .orElseThrow(NoteNotFoundException::new);
    return mapper.toResponse(replaced, quoted);
  }

  @Override
  public void delete(UUID userId, UUID id) {
    if (!noteService.delete(userId, id)) {
      throw new NoteNotFoundException();
    }
  }

  private NoteContent contentOf(NoteRequest request) {
    NoteContent content = mapper.toContent(request);
    Optional<String> problem = content.violation();
    if (problem.isPresent()) {
      throw new InvalidNoteException(problem.get());
    }
    return content;
  }
}
