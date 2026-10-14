package app.ari.api.feature.source.presentation.impl;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.model.request.ListSourcesRequest;
import app.ari.api.feature.source.application.model.response.SourceCountsResponse;
import app.ari.api.feature.source.application.model.response.SourceSummaryResponse;
import app.ari.api.feature.source.presentation.SourceController;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SourceControllerImpl implements SourceController {

  private final SourceFacade facade;

  public SourceControllerImpl(SourceFacade facade) {
    this.facade = facade;
  }

  @Override
  public PageResponse<SourceSummaryResponse> list(Jwt jwt, @Valid ListSourcesRequest request) {
    return facade.list(userId(jwt), request);
  }

  @Override
  public SourceCountsResponse counts(Jwt jwt) {
    return facade.counts(userId(jwt));
  }

  @Override
  public SourceSummaryResponse get(Jwt jwt, UUID id) {
    return facade.get(userId(jwt), id);
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
