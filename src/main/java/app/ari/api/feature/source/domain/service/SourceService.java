package app.ari.api.feature.source.domain.service;

import app.ari.api.feature.source.domain.model.Source;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Finds or creates the source a save belongs to. */
public interface SourceService {

  /**
   * Returns the user's source for this web page, creating it the first time; empty when {@code
   * address} is not an http or https address. A later save from the same page updates its name,
   * title and, when given, author.
   */
  Optional<Source> saveWebsite(
      UUID userId, String address, String name, String title, String author);

  List<Source> findAllByIds(UUID userId, Collection<UUID> ids);
}
