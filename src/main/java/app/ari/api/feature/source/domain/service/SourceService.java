package app.ari.api.feature.source.domain.service;

import app.ari.api.feature.source.domain.model.Source;
import app.ari.api.feature.source.domain.model.SourceCursor;
import app.ari.api.feature.source.domain.model.SourceKind;
import app.ari.api.feature.source.domain.model.SourceSummary;
import app.ari.api.feature.source.domain.model.SourceSummaryPage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Finds or creates the source a save belongs to, and sums up the sources for the Sources page. */
public interface SourceService {

  /**
   * Returns the user's source for this web page, creating it the first time; empty when {@code
   * address} is not an http or https address. A later save from the same page updates its name,
   * title and, when given, author.
   */
  Optional<Source> saveWebsite(
      UUID userId, String address, String name, String title, String author);

  List<Source> findAllByIds(UUID userId, Collection<UUID> ids);

  /** The user's sources whose name, title or author the query's words start; none without words. */
  List<UUID> findIdsMatching(UUID userId, String query);

  /**
   * The user's sources that have saves, of {@code kind} unless it is null, the one saved from most
   * recently first, after the cursor when given. With a {@code query} (null for none), only those
   * whose name, title or author have words its words start.
   */
  SourceSummaryPage page(UUID userId, SourceKind kind, String query, SourceCursor after, int limit);

  /** Empty when the source is unknown, someone else's, or has no saves any more. */
  Optional<SourceSummary> summary(UUID userId, UUID id);

  /** How many sources with saves the user has of each kind; every kind is there, 0 included. */
  Map<SourceKind, Integer> countByKind(UUID userId);
}
