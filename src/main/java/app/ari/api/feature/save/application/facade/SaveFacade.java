package app.ari.api.feature.save.application.facade;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import java.util.UUID;

/** The Library's use cases. Other features use only this facade. */
public interface SaveFacade {

  /**
   * Saves a highlight or clip from a web page and counts it in the user's saves. {@code image} is
   * the picture of an IMAGE clip and null otherwise. Throws {@code InvalidSaveException}, {@code
   * InvalidClipImageException} or {@code InvalidSourceUrlException}.
   */
  SaveResponse create(UUID userId, CreateSaveRequest request, byte[] image);

  /** The user's saves, newest first; throws {@code InvalidCursorException}. */
  PageResponse<SaveResponse> list(UUID userId, ListSavesRequest request);

  FacetsResponse facets(UUID userId, SaveFilterRequest filter);

  /** One save with its source and tags; throws {@code SaveNotFoundException}. */
  SaveResponse get(UUID userId, UUID id);

  /** Throws {@code SaveNotFoundException} or {@code InvalidSaveException}. */
  SaveResponse update(UUID userId, UUID id, UpdateSaveRequest request);

  /** Deletes a save and its image; throws {@code SaveNotFoundException}. */
  void delete(UUID userId, UUID id);
}
