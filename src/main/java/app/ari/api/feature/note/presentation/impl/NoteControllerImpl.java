package app.ari.api.feature.note.presentation.impl;

import app.ari.api.feature.note.application.facade.NoteFacade;
import app.ari.api.feature.note.application.model.request.ListNotesRequest;
import app.ari.api.feature.note.application.model.request.NoteRequest;
import app.ari.api.feature.note.application.model.response.NoteResponse;
import app.ari.api.feature.note.application.model.response.NoteSummaryResponse;
import app.ari.api.feature.note.presentation.NoteController;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoteControllerImpl implements NoteController {

  private final NoteFacade facade;

  public NoteControllerImpl(NoteFacade facade) {
    this.facade = facade;
  }

  @Override
  public List<NoteSummaryResponse> list(Jwt jwt, @Valid ListNotesRequest request) {
    return facade.list(userId(jwt), request);
  }

  @Override
  public ResponseEntity<NoteResponse> create(Jwt jwt, @Valid NoteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(facade.create(userId(jwt), request));
  }

  @Override
  public NoteResponse get(Jwt jwt, UUID id) {
    return facade.get(userId(jwt), id);
  }

  @Override
  public NoteResponse replace(Jwt jwt, UUID id, @Valid NoteRequest request) {
    return facade.replace(userId(jwt), id, request);
  }

  @Override
  public ResponseEntity<Void> delete(Jwt jwt, UUID id) {
    facade.delete(userId(jwt), id);
    return ResponseEntity.noContent().build();
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
