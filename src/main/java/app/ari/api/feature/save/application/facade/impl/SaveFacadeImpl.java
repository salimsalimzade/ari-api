package app.ari.api.feature.save.application.facade.impl;

import app.ari.api.common.response.PageResponse;
import app.ari.api.common.transaction.AfterCommit;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.save.application.exception.badrequest.InvalidClipImageException;
import app.ari.api.feature.save.application.exception.badrequest.InvalidCursorException;
import app.ari.api.feature.save.application.exception.badrequest.InvalidSaveException;
import app.ari.api.feature.save.application.exception.notfound.SaveNotFoundException;
import app.ari.api.feature.save.application.facade.SaveFacade;
import app.ari.api.feature.save.application.mapper.SaveMapper;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import app.ari.api.feature.save.domain.model.ClipImage;
import app.ari.api.feature.save.domain.model.NewSave;
import app.ari.api.feature.save.domain.model.Save;
import app.ari.api.feature.save.domain.model.SaveChange;
import app.ari.api.feature.save.domain.model.SaveCursor;
import app.ari.api.feature.save.domain.model.SaveFacets;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.SavePage;
import app.ari.api.feature.save.domain.model.SourceCount;
import app.ari.api.feature.save.domain.model.TaggedSave;
import app.ari.api.feature.save.domain.service.SaveService;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and deleting run in one transaction with the user's saves count, so the sidebar number
 * never drifts from the saves. A deleted save's image file is removed after that transaction
 * commits.
 */
@Service
public class SaveFacadeImpl implements SaveFacade {

  private final SaveService saveService;
  private final SourceFacade sources;
  private final AccountFacade accounts;
  private final SaveMapper mapper;

  public SaveFacadeImpl(
      SaveService saveService, SourceFacade sources, AccountFacade accounts, SaveMapper mapper) {
    this.saveService = saveService;
    this.sources = sources;
    this.accounts = accounts;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public SaveResponse create(UUID userId, CreateSaveRequest request, byte[] image) {
    ClipImage clipImage =
        image == null ? null : ClipImage.of(image).orElseThrow(InvalidClipImageException::new);
    NewSave newSave = mapper.toNewSave(request);
    Optional<String> problem = newSave.violation(clipImage != null);
    if (problem.isPresent()) {
      throw new InvalidSaveException(problem.get());
    }
    SourceResponse source = sources.saveWebsite(userId, request.source());
    accounts.changeSavesCount(userId, 1);
    // Last: it may store the image, which nothing after it could take back.
    TaggedSave created = saveService.create(userId, source.id(), newSave, clipImage);
    return toResponse(created, source);
  }

  @Override
  public PageResponse<SaveResponse> list(UUID userId, ListSavesRequest request) {
    SaveCursor after =
        request.cursor() == null
            ? null
            : SaveCursor.decode(request.cursor()).orElseThrow(InvalidCursorException::new);
    SavePage page = saveService.page(userId, mapper.toFilter(request), after, request.pageSize());
    Map<UUID, SourceResponse> pageSources =
        sources.findAll(
            userId,
            page.items().stream().map(item -> item.save().sourceId()).collect(Collectors.toSet()));
    List<SaveResponse> items =
        page.items().stream()
            .map(item -> toResponse(item, pageSources.get(item.save().sourceId())))
            .toList();
    return new PageResponse<>(items, page.next() == null ? null : page.next().encode());
  }

  @Override
  public FacetsResponse facets(UUID userId, SaveFilterRequest request) {
    SaveFilter filter = mapper.toFilter(request);
    SaveFacets facets = saveService.facets(userId, filter);
    Set<UUID> sourceIds =
        facets.sources().stream().map(SourceCount::sourceId).collect(Collectors.toSet());
    return mapper.toFacetsResponse(facets, sources.findAll(userId, sourceIds));
  }

  @Override
  public SaveResponse get(UUID userId, UUID id) {
    TaggedSave tagged = saveService.find(userId, id).orElseThrow(SaveNotFoundException::new);
    UUID sourceId = tagged.save().sourceId();
    return toResponse(tagged, sources.findAll(userId, Set.of(sourceId)).get(sourceId));
  }

  @Override
  @Transactional
  public SaveResponse update(UUID userId, UUID id, UpdateSaveRequest request) {
    Save save = saveService.findById(userId, id).orElseThrow(SaveNotFoundException::new);
    SaveChange change = mapper.toChange(request);
    Optional<String> problem = change.violationFor(save);
    if (problem.isPresent()) {
      throw new InvalidSaveException(problem.get());
    }
    TaggedSave updated = saveService.update(save, change);
    SourceResponse source = sources.findAll(userId, Set.of(save.sourceId())).get(save.sourceId());
    return toResponse(updated, source);
  }

  @Override
  @Transactional
  public void delete(UUID userId, UUID id) {
    Save deleted = saveService.delete(userId, id).orElseThrow(SaveNotFoundException::new);
    accounts.changeSavesCount(userId, -1);
    AfterCommit.run(() -> saveService.discardImage(deleted));
  }

  private SaveResponse toResponse(TaggedSave tagged, SourceResponse source) {
    return mapper.toResponse(tagged, source, saveService.imageUrl(tagged.save()).orElse(null));
  }
}
