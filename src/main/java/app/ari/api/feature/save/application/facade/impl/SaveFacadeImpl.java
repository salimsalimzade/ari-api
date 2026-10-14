package app.ari.api.feature.save.application.facade.impl;

import app.ari.api.common.response.PageResponse;
import app.ari.api.common.transaction.AfterCommit;
import app.ari.api.common.transaction.OnRollback;
import app.ari.api.feature.account.application.facade.AccountFacade;
import app.ari.api.feature.save.application.exception.badrequest.InvalidClipImageException;
import app.ari.api.feature.save.application.exception.badrequest.InvalidCursorException;
import app.ari.api.feature.save.application.exception.badrequest.InvalidSaveException;
import app.ari.api.feature.save.application.exception.notfound.SaveNotFoundException;
import app.ari.api.feature.save.application.exception.payloadtoolarge.ClipImageTooLargeException;
import app.ari.api.feature.save.application.facade.SaveFacade;
import app.ari.api.feature.save.application.mapper.SaveMapper;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ImportedSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import app.ari.api.feature.save.domain.model.ClipImage;
import app.ari.api.feature.save.domain.model.ClipType;
import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.NewSave;
import app.ari.api.feature.save.domain.model.Save;
import app.ari.api.feature.save.domain.model.SaveChange;
import app.ari.api.feature.save.domain.model.SaveCursor;
import app.ari.api.feature.save.domain.model.SaveFacets;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.SaveKind;
import app.ari.api.feature.save.domain.model.SaveOrder;
import app.ari.api.feature.save.domain.model.SavePage;
import app.ari.api.feature.save.domain.model.SourceCount;
import app.ari.api.feature.save.domain.model.TaggedSave;
import app.ari.api.feature.save.domain.service.SaveService;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.ArrayList;
import java.util.Collection;
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
    // The servlet takes uploads up to 50 MB, for imported files.
    if (image != null && image.length > ClipImage.MAX_BYTES) {
      throw new ClipImageTooLargeException();
    }
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
    TaggedSave created = saveService.create(userId, source.id(), newSave, clipImage, null);
    return toResponse(created, source);
  }

  @Override
  @Transactional
  public List<UUID> createImported(UUID userId, UUID sourceId, List<ImportedSaveRequest> requests) {
    List<UUID> ids = new ArrayList<>();
    for (ImportedSaveRequest request : requests) {
      ClipImage picture =
          request.image() == null
              ? null
              : ClipImage.of(request.image()).orElseThrow(InvalidClipImageException::new);
      NewSave newSave =
          picture == null
              ? new NewSave(
                  SaveKind.IMPORT,
                  HighlightColor.valueOf(request.color()),
                  null,
                  request.text(),
                  null,
                  request.comment(),
                  null,
                  request.tags())
              : new NewSave(
                  SaveKind.CLIP,
                  null,
                  ClipType.IMAGE,
                  request.text(),
                  null,
                  request.comment(),
                  null,
                  request.tags());
      Save created =
          saveService.create(userId, sourceId, newSave, picture, request.pageNumber()).save();
      // The pictures are stored as they go; a later failure takes them back.
      if (created.imageKey() != null) {
        OnRollback.run(() -> saveService.discardImage(created));
      }
      ids.add(created.id());
    }
    accounts.changeSavesCount(userId, ids.size());
    return ids;
  }

  @Override
  public PageResponse<SaveResponse> list(UUID userId, ListSavesRequest request) {
    SaveOrder order = request.ordering();
    // A cursor goes on only in the order it was made for.
    SaveCursor after =
        request.cursor() == null
            ? null
            : SaveCursor.decode(request.cursor())
                .filter(cursor -> cursor.order() == order)
                .orElseThrow(InvalidCursorException::new);
    SavePage page =
        saveService.page(
            userId,
            matchingSources(userId, mapper.toFilter(request)),
            order,
            after,
            request.pageSize());
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
    SaveFilter filter = matchingSources(userId, mapper.toFilter(request));
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
  public Map<UUID, SaveResponse> findAll(UUID userId, Collection<UUID> ids) {
    List<TaggedSave> found = saveService.findAll(userId, ids);
    Map<UUID, SourceResponse> foundSources =
        sources.findAll(
            userId, found.stream().map(item -> item.save().sourceId()).collect(Collectors.toSet()));
    return found.stream()
        .collect(
            Collectors.toMap(
                item -> item.save().id(),
                item -> toResponse(item, foundSources.get(item.save().sourceId()))));
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

  /** A search also finds the saves of the sources it matches, which the source feature knows. */
  private SaveFilter matchingSources(UUID userId, SaveFilter filter) {
    if (filter.query() == null) {
      return filter;
    }
    return new SaveFilter(
        filter.kind(),
        filter.tags(),
        filter.colors(),
        filter.sourceIds(),
        filter.query(),
        sources.findIdsMatching(userId, filter.query()));
  }

  private SaveResponse toResponse(TaggedSave tagged, SourceResponse source) {
    return mapper.toResponse(tagged, source, saveService.imageUrl(tagged.save()).orElse(null));
  }
}
