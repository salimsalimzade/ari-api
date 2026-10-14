package app.ari.api.feature.fileimport.presentation.impl;

import app.ari.api.feature.fileimport.application.facade.ImportFacade;
import app.ari.api.feature.fileimport.application.model.request.ReviewRequest;
import app.ari.api.feature.fileimport.application.model.response.ImportResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportsResponse;
import app.ari.api.feature.fileimport.application.model.response.ReviewResponse;
import app.ari.api.feature.fileimport.presentation.ImportController;
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
public class ImportControllerImpl implements ImportController {

  private final ImportFacade facade;

  public ImportControllerImpl(ImportFacade facade) {
    this.facade = facade;
  }

  @Override
  public ImportsResponse list(Jwt jwt) {
    return facade.list(userId(jwt));
  }

  @Override
  public ResponseEntity<ImportResponse> upload(Jwt jwt, MultipartFile file) {
    try {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(facade.upload(userId(jwt), file.getOriginalFilename(), file.getBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the uploaded file", e);
    }
  }

  @Override
  public ImportResponse get(Jwt jwt, UUID id) {
    return facade.get(userId(jwt), id);
  }

  @Override
  public ReviewResponse review(Jwt jwt, UUID id, @Valid ReviewRequest request) {
    return facade.review(userId(jwt), id, request);
  }

  @Override
  public ResponseEntity<ImportResponse> retry(Jwt jwt, UUID id) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(facade.retry(userId(jwt), id));
  }

  @Override
  public ResponseEntity<Void> dismiss(Jwt jwt, UUID id) {
    facade.dismiss(userId(jwt), id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> discard(Jwt jwt, UUID id) {
    facade.discard(userId(jwt), id);
    return ResponseEntity.noContent().build();
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
