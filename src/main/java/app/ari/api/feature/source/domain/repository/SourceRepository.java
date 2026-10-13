package app.ari.api.feature.source.domain.repository;

import app.ari.api.feature.source.domain.model.Source;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Stores sources. */
public interface SourceRepository {

  /**
   * Adds the web page, or, when the user already has a source with its address, keeps that row and
   * brings its name, title and (when given) author up to date. Returns the stored row.
   */
  Source upsertWebsite(Source source);

  /**
   * The user's own sources among {@code ids}; ids that are unknown or someone else's are left out.
   */
  List<Source> findAllByIds(UUID userId, Collection<UUID> ids);
}
