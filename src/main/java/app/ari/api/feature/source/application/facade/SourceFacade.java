package app.ari.api.feature.source.application.facade;

import app.ari.api.feature.source.application.model.request.SourceRequest;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Sources for the features that save from them. Other features use only this facade. */
public interface SourceFacade {

  /**
   * The user's source for this web page, created the first time; throws {@code
   * InvalidSourceUrlException} unless the address is an http or https one.
   */
  SourceResponse saveWebsite(UUID userId, SourceRequest request);

  /** The user's own sources among {@code ids}, by id; unknown ids are left out. */
  Map<UUID, SourceResponse> findAll(UUID userId, Collection<UUID> ids);
}
