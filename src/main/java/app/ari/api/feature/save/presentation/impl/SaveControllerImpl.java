package app.ari.api.feature.save.presentation.impl;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.save.application.facade.SaveFacade;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import app.ari.api.feature.save.presentation.SaveController;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SaveControllerImpl implements SaveController {

  private final SaveFacade facade;

  public SaveControllerImpl(SaveFacade facade) {
    this.facade = facade;
  }

  @Override
  public ResponseEntity<SaveResponse> create(Jwt jwt, @Valid CreateSaveRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(facade.create(userId(jwt), request, null));
  }

  @Override
  public ResponseEntity<SaveResponse> createWithImage(
      Jwt jwt, @Valid CreateSaveRequest save, MultipartFile image) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(facade.create(userId(jwt), save, image.getBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the uploaded image", e);
    }
  }

  @Override
  public PageResponse<SaveResponse> list(Jwt jwt, @Valid ListSavesRequest request) {
    return facade.list(userId(jwt), request);
  }

  @Override
  public FacetsResponse facets(Jwt jwt, SaveFilterRequest filter) {
    return facade.facets(userId(jwt), filter);
  }

  @Override
  public SaveResponse get(Jwt jwt, UUID id) {
    return facade.get(userId(jwt), id);
  }

  @Override
  public SaveResponse update(Jwt jwt, UUID id, @Valid UpdateSaveRequest request) {
    return facade.update(userId(jwt), id, request);
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
