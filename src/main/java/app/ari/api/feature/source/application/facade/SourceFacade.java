package app.ari.api.feature.source.application.facade;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.source.application.model.request.ListSourcesRequest;
import app.ari.api.feature.source.application.model.request.SourceRequest;
import app.ari.api.feature.source.application.model.response.SourceCountsResponse;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import app.ari.api.feature.source.application.model.response.SourceSummaryResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sources, for the features that save from them and for the Sources page. Other features use only
 * this facade.
 */
public interface SourceFacade {

  /**
   * The user's source for this web page, created the first time; throws {@code
   * InvalidSourceUrlException} unless the address is an http or https one.
   */
  SourceResponse saveWebsite(UUID userId, SourceRequest request);

  /**
   * A new source for an imported file: {@code name} is the file's name, {@code title} and {@code
   * author} what the file says (null for none; the title then falls back to the name).
   */
  SourceResponse createFile(UUID userId, String name, String title, String author);

  /**
   * Changes an imported file's title and author (a blank author removes it); empty when the user
   * has no such file source.
   */
  Optional<SourceResponse> updateFile(UUID userId, UUID id, String title, String author);

  /** Deletes an imported file's source unless something is saved from it. */
  void deleteFileIfUnused(UUID userId, UUID id);

  /** The user's own sources among {@code ids}, by id; unknown ids are left out. */
  Map<UUID, SourceResponse> findAll(UUID userId, Collection<UUID> ids);

  /**
   * The user's sources whose name, title or author the words of {@code query} start, for the
   * Library's search.
   */
  List<UUID> findIdsMatching(UUID userId, String query);

  /**
   * The user's sources that have saves, the one saved from most recently first; throws {@code
   * InvalidCursorException}.
   */
  PageResponse<SourceSummaryResponse> list(UUID userId, ListSourcesRequest request);

  /** How many sources with saves the user has, of each kind. */
  SourceCountsResponse counts(UUID userId);

  /** One source with what is saved from it; throws {@code SourceNotFoundException}. */
  SourceSummaryResponse get(UUID userId, UUID id);
}
