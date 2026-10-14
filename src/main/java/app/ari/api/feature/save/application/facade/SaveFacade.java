package app.ari.api.feature.save.application.facade;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ImportedSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The Library's use cases. Other features use only this facade. */
public interface SaveFacade {

  /**
   * Saves a highlight or clip from a web page and counts it in the user's saves. {@code image} is
   * the picture of an IMAGE clip and null otherwise. Throws {@code InvalidSaveException}, {@code
   * InvalidClipImageException} or {@code InvalidSourceUrlException}.
   */
  SaveResponse create(UUID userId, CreateSaveRequest request, byte[] image);

  /**
   * Saves marks of an imported file, all from its source, and counts them in the user's saves;
   * returns the new saves' ids in the same order. Throws {@code InvalidClipImageException} for a
   * picture that isn't a supported image.
   */
  List<UUID> createImported(UUID userId, UUID sourceId, List<ImportedSaveRequest> saves);

  /**
   * The user's saves, newest first or in page order; throws {@code InvalidCursorException}, also
   * for a cursor made for the other order.
   */
  PageResponse<SaveResponse> list(UUID userId, ListSavesRequest request);

  FacetsResponse facets(UUID userId, SaveFilterRequest filter);

  /** One save with its source and tags; throws {@code SaveNotFoundException}. */
  SaveResponse get(UUID userId, UUID id);

  /**
   * The user's own saves among {@code ids}, with their sources and tags, by id; unknown ids and
   * someone else's are left out.
   */
  Map<UUID, SaveResponse> findAll(UUID userId, Collection<UUID> ids);

  /** Throws {@code SaveNotFoundException} or {@code InvalidSaveException}. */
  SaveResponse update(UUID userId, UUID id, UpdateSaveRequest request);

  /** Deletes a save and its image; throws {@code SaveNotFoundException}. */
  void delete(UUID userId, UUID id);
}
