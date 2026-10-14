package app.ari.api.feature.source.domain.repository;

import app.ari.api.feature.source.domain.model.KindCount;
import app.ari.api.feature.source.domain.model.LatestHighlight;
import app.ari.api.feature.source.domain.model.Source;
import app.ari.api.feature.source.domain.model.SourceColor;
import app.ari.api.feature.source.domain.model.SourceCursor;
import app.ari.api.feature.source.domain.model.SourceKind;
import app.ari.api.feature.source.domain.model.SourceNote;
import app.ari.api.feature.source.domain.model.SourceStats;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores sources. What the Sources page shows of a source is summed up from the saves made from it
 * and the notes quoting them, which this port reads but never changes.
 */
public interface SourceRepository {

  /**
   * Adds the web page, or, when the user already has a source with its address, keeps that row and
   * brings its name, title and (when given) author up to date. Returns the stored row.
   */
  Source upsertWebsite(Source source);

  /** Adds a source that has no address: an imported file. */
  void insert(Source source);

  /** Saves the name, title, author and update time. */
  void updateDetails(Source source);

  /** Deletes the user's FILE source unless saves point at it. */
  void deleteFileIfUnused(UUID userId, UUID id);

  /**
   * The user's own sources among {@code ids}; ids that are unknown or someone else's are left out.
   */
  List<Source> findAllByIds(UUID userId, Collection<UUID> ids);

  /**
   * Up to {@code limit} of the user's sources that have saves, of {@code kind} unless it is null,
   * and whose search words match {@code prefixQuery} (a {@code to_tsquery} text) unless it is null,
   * the one saved from most recently first. When {@code after} is given, only the sources past it:
   * keyset paging on (the latest save's time, id).
   */
  List<SourceStats> findStatsPage(
      UUID userId, SourceKind kind, String prefixQuery, SourceCursor after, int limit);

  /** Empty when the source is unknown, someone else's, or has no saves. */
  Optional<SourceStats> findStats(UUID userId, UUID id);

  /** The colors the saves from each of these sources use, most used first. */
  List<SourceColor> findColors(UUID userId, Collection<UUID> sourceIds);

  /** The newest save with a color from each of these sources; a source with none is left out. */
  List<LatestHighlight> findLatestHighlights(UUID userId, Collection<UUID> sourceIds);

  /**
   * The user's notes that quote saves from each of these sources, the one changed most recently
   * first.
   */
  List<SourceNote> findNotes(UUID userId, Collection<UUID> sourceIds);

  /** The user's sources whose search words match {@code prefixQuery}, a {@code to_tsquery} text. */
  List<UUID> findIdsMatching(UUID userId, String prefixQuery);

  /** The user's sources that have saves, per kind; a kind with none is left out. */
  List<KindCount> countByKind(UUID userId);
}
