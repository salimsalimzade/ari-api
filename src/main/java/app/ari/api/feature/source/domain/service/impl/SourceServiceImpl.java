package app.ari.api.feature.source.domain.service.impl;

import app.ari.api.common.search.SearchText;
import app.ari.api.feature.source.domain.model.LatestHighlight;
import app.ari.api.feature.source.domain.model.Source;
import app.ari.api.feature.source.domain.model.SourceColor;
import app.ari.api.feature.source.domain.model.SourceCursor;
import app.ari.api.feature.source.domain.model.SourceKind;
import app.ari.api.feature.source.domain.model.SourceNote;
import app.ari.api.feature.source.domain.model.SourceStats;
import app.ari.api.feature.source.domain.model.SourceSummary;
import app.ari.api.feature.source.domain.model.SourceSummaryPage;
import app.ari.api.feature.source.domain.model.SourceUrl;
import app.ari.api.feature.source.domain.repository.SourceRepository;
import app.ari.api.feature.source.domain.service.SourceService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SourceServiceImpl implements SourceService {

  // The columns' lengths: an imported file's name, title and author come from the file.
  private static final int NAME_LENGTH = 255;
  private static final int TITLE_LENGTH = 500;
  private static final int AUTHOR_LENGTH = 255;

  private final SourceRepository sources;
  private final Clock clock;

  public SourceServiceImpl(SourceRepository sources, Clock clock) {
    this.sources = sources;
    this.clock = clock;
  }

  @Override
  public Optional<Source> saveWebsite(
      UUID userId, String address, String name, String title, String author) {
    return SourceUrl.normalize(address)
        .map(
            url -> {
              Instant now = clock.instant();
              String writer = author == null || author.isBlank() ? null : author.strip();
              return sources.upsertWebsite(
                  new Source(
                      UUID.randomUUID(),
                      userId,
                      SourceKind.WEBSITE,
                      name.strip(),
                      title.strip(),
                      writer,
                      url,
                      now,
                      now));
            });
  }

  @Override
  public Source createFile(UUID userId, String name, String title, String author) {
    Instant now = clock.instant();
    String fileName = clip(name.strip(), NAME_LENGTH);
    Source source =
        new Source(
            UUID.randomUUID(),
            userId,
            SourceKind.FILE,
            fileName,
            title == null || title.isBlank() ? fileName : clip(title.strip(), TITLE_LENGTH),
            blankToNull(author),
            null,
            now,
            now);
    sources.insert(source);
    return source;
  }

  @Override
  public Optional<Source> updateFile(UUID userId, UUID id, String title, String author) {
    return sources.findAllByIds(userId, List.of(id)).stream()
        .filter(source -> source.kind() == SourceKind.FILE)
        .findFirst()
        .map(
            source -> {
              Source changed =
                  new Source(
                      source.id(),
                      userId,
                      source.kind(),
                      source.name(),
                      title == null || title.isBlank()
                          ? source.name()
                          : clip(title.strip(), TITLE_LENGTH),
                      blankToNull(author),
                      null,
                      source.createdAt(),
                      clock.instant());
              sources.updateDetails(changed);
              return changed;
            });
  }

  @Override
  public void deleteFileIfUnused(UUID userId, UUID id) {
    sources.deleteFileIfUnused(userId, id);
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : clip(text.strip(), AUTHOR_LENGTH);
  }

  /** Cuts text to fit its column. */
  private static String clip(String text, int length) {
    return text.length() <= length ? text : text.substring(0, length);
  }

  @Override
  public List<Source> findAllByIds(UUID userId, Collection<UUID> ids) {
    return ids.isEmpty() ? List.of() : sources.findAllByIds(userId, ids);
  }

  @Override
  public List<UUID> findIdsMatching(UUID userId, String query) {
    String prefixQuery = SearchText.prefixQuery(query);
    return prefixQuery == null ? List.of() : sources.findIdsMatching(userId, prefixQuery);
  }

  @Override
  public SourceSummaryPage page(
      UUID userId, SourceKind kind, String query, SourceCursor after, int limit) {
    // One more than asked for tells whether there is a next page.
    List<SourceStats> rows =
        sources.findStatsPage(userId, kind, SearchText.prefixQuery(query), after, limit + 1);
    boolean more = rows.size() > limit;
    List<SourceStats> shown = more ? rows.subList(0, limit) : rows;
    SourceCursor next =
        more ? new SourceCursor(shown.getLast().lastSavedAt(), shown.getLast().sourceId()) : null;
    return new SourceSummaryPage(summarize(userId, shown), next);
  }

  @Override
  public Optional<SourceSummary> summary(UUID userId, UUID id) {
    return sources.findStats(userId, id).map(stats -> summarize(userId, List.of(stats)).getFirst());
  }

  @Override
  public Map<SourceKind, Integer> countByKind(UUID userId) {
    Map<SourceKind, Integer> counts = new EnumMap<>(SourceKind.class);
    for (SourceKind kind : SourceKind.values()) {
      counts.put(kind, 0);
    }
    sources.countByKind(userId).forEach(row -> counts.put(row.kind(), row.count()));
    return counts;
  }

  /** Adds each source's row, colors, latest highlight and notes to its stats, in the same order. */
  private List<SourceSummary> summarize(UUID userId, List<SourceStats> stats) {
    if (stats.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = stats.stream().map(SourceStats::sourceId).toList();
    Map<UUID, Source> byId =
        sources.findAllByIds(userId, ids).stream()
            .collect(Collectors.toMap(Source::id, Function.identity()));
    Map<UUID, List<String>> colors =
        sources.findColors(userId, ids).stream()
            .collect(
                Collectors.groupingBy(
                    SourceColor::sourceId,
                    Collectors.mapping(SourceColor::color, Collectors.toList())));
    Map<UUID, LatestHighlight> latest =
        sources.findLatestHighlights(userId, ids).stream()
            .collect(Collectors.toMap(LatestHighlight::sourceId, Function.identity()));
    Map<UUID, List<SourceNote>> notes =
        sources.findNotes(userId, ids).stream()
            .collect(Collectors.groupingBy(SourceNote::sourceId));
    return stats.stream()
        .map(
            row ->
                new SourceSummary(
                    byId.get(row.sourceId()),
                    row,
                    colors.getOrDefault(row.sourceId(), List.of()),
                    latest.get(row.sourceId()),
                    notes.getOrDefault(row.sourceId(), List.of())))
        .toList();
  }
}
