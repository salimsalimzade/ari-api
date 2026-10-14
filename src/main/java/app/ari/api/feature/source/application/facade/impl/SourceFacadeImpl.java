package app.ari.api.feature.source.application.facade.impl;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.source.application.exception.badrequest.InvalidCursorException;
import app.ari.api.feature.source.application.exception.badrequest.InvalidSourceUrlException;
import app.ari.api.feature.source.application.exception.notfound.SourceNotFoundException;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.mapper.SourceMapper;
import app.ari.api.feature.source.application.model.request.ListSourcesRequest;
import app.ari.api.feature.source.application.model.request.SourceRequest;
import app.ari.api.feature.source.application.model.response.SourceCountsResponse;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import app.ari.api.feature.source.application.model.response.SourceSummaryResponse;
import app.ari.api.feature.source.domain.model.SourceCursor;
import app.ari.api.feature.source.domain.model.SourceSummaryPage;
import app.ari.api.feature.source.domain.service.SourceService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  public SourceResponse createFile(UUID userId, String name, String title, String author) {
    return mapper.toResponse(sourceService.createFile(userId, name, title, author));
  }

  @Override
  public Optional<SourceResponse> updateFile(UUID userId, UUID id, String title, String author) {
    return sourceService.updateFile(userId, id, title, author).map(mapper::toResponse);
  }

  @Override
  public void deleteFileIfUnused(UUID userId, UUID id) {
    sourceService.deleteFileIfUnused(userId, id);
  }

  @Override
  public Map<UUID, SourceResponse> findAll(UUID userId, Collection<UUID> ids) {
    return sourceService.findAllByIds(userId, ids).stream()
        .map(mapper::toResponse)
        .collect(Collectors.toMap(SourceResponse::id, Function.identity()));
  }

  @Override
  public List<UUID> findIdsMatching(UUID userId, String query) {
    return sourceService.findIdsMatching(userId, query);
  }

  @Override
  public PageResponse<SourceSummaryResponse> list(UUID userId, ListSourcesRequest request) {
    SourceCursor after =
        request.cursor() == null
            ? null
            : SourceCursor.decode(request.cursor()).orElseThrow(InvalidCursorException::new);
    SourceSummaryPage page =
        sourceService.page(userId, request.kind(), request.q(), after, request.pageSize());
    return new PageResponse<>(
        page.items().stream().map(mapper::toSummaryResponse).toList(),
        page.next() == null ? null : page.next().encode());
  }

  @Override
  public SourceCountsResponse counts(UUID userId) {
    return mapper.toCountsResponse(sourceService.countByKind(userId));
  }

  @Override
  public SourceSummaryResponse get(UUID userId, UUID id) {
    return sourceService
        .summary(userId, id)
        .map(mapper::toSummaryResponse)
        .orElseThrow(SourceNotFoundException::new);
  }
}
