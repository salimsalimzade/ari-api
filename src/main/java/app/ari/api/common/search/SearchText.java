package app.ari.api.common.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What someone types to search, in the shapes the SQL needs. Every word is matched as the start of
 * a word; the database takes accents off both sides ({@code ari_unaccent}).
 */
public final class SearchText {

  private static final int MAX_WORDS = 10;

  private SearchText() {}

  /**
   * The query's words, lowercase: its letters (with their marks) and digits, at most 10 words. A
   * capital İ lowercases to i with a combining dot, which no text has, so the dot goes.
   */
  public static List<String> words(String query) {
    if (query == null) {
      return List.of();
    }
    String lowercase = query.toLowerCase(Locale.ROOT).replace("i\u0307", "i");
    return Arrays.stream(lowercase.split("[^\\p{L}\\p{M}\\p{N}]+"))
        .filter(word -> !word.isEmpty())
        .limit(MAX_WORDS)
        .toList();
  }

  /**
   * A {@code to_tsquery} text that every word must start a word of, e.g. {@code swarm:* & nest:*};
   * null when the query has no words. Only letters and digits get in, so nothing can break the
   * query's syntax.
   */
  public static String prefixQuery(String query) {
    List<String> words = words(query);
    return words.isEmpty() ? null : String.join(" & ", words.stream().map(w -> w + ":*").toList());
  }

  /**
   * A {@code LIKE} pattern for the whole query anywhere, lowercase, with {@code \}, {@code %} and
   * {@code _} escaped by {@code \}, e.g. {@code %nest site%}.
   */
  public static String containsPattern(String query) {
    String escaped =
        query
            .strip()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
