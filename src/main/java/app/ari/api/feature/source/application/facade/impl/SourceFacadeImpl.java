package app.ari.api.feature.source.application.facade.impl;

import app.ari.api.feature.source.application.exception.badrequest.InvalidSourceUrlException;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.mapper.SourceMapper;
import app.ari.api.feature.source.application.model.request.SourceRequest;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import app.ari.api.feature.source.domain.service.SourceService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SourceFacadeImpl implements SourceFacade {

  private final SourceService sourceService;
  private final SourceMapper mapper;

  public SourceFacadeImpl(SourceService sourceService, SourceMapper mapper) {
    this.sourceService = sourceService;
    this.mapper = mapper;
  }

  @Override
  public SourceResponse saveWebsite(UUID userId, SourceRequest request) {
    return sourceService
        .saveWebsite(userId, request.url(), request.name(), request.title(), request.author())
        .map(mapper::toResponse)
        .orElseThrow(InvalidSourceUrlException::new);
  }

  @Override
  public Map<UUID, SourceResponse> findAll(UUID userId, Collection<UUID> ids) {
    return sourceService.findAllByIds(userId, ids).stream()
        .map(mapper::toResponse)
        .collect(Collectors.toMap(SourceResponse::id, Function.identity()));
  }
}
