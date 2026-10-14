package app.ari.api.feature.save.domain.model;

import app.ari.api.common.search.SearchText;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What the Library is narrowed to. Within tags, colors or sources a save needs to match any one of
 * them; across them it needs to match each group that is given. {@code kind} is the type tab. Tag
 * names are compared in lowercase. {@code query} is a search, null without one (or without a word
 * in it): a save matches when the query's words start words of its text, comment or section, when
 * the whole query is part of its text, or when it comes from one of {@code sourcesMatchingQuery},
 * the sources whose name, title or author the query's words start.
 */
public record SaveFilter(
    SaveKind kind,
    List<String> tags,
    List<HighlightColor> colors,
    List<UUID> sourceIds,
    String query,
    List<UUID> sourcesMatchingQuery) {

  public SaveFilter {
    tags =
        tags == null
            ? List.of()
            : tags.stream()
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    colors = colors == null ? List.of() : List.copyOf(colors);
    sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    query = SearchText.words(query).isEmpty() ? null : query.strip();
    sourcesMatchingQuery =
        sourcesMatchingQuery == null ? List.of() : List.copyOf(sourcesMatchingQuery);
  }

  /** The same filter for every type, for counting the type tabs. */
  public SaveFilter withoutKind() {
    return new SaveFilter(null, tags, colors, sourceIds, query, sourcesMatchingQuery);
  }

  /** The query for {@code to_tsquery}; see {@link SearchText#prefixQuery}. */
  public String prefixQuery() {
    return SearchText.prefixQuery(query);
  }

  /** The query for {@code LIKE}; see {@link SearchText#containsPattern}. */
  public String containsPattern() {
    return query == null ? null : SearchText.containsPattern(query);
  }
}
