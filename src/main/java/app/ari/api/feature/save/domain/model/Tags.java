package app.ari.api.feature.save.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tag names as Ari stores them. */
public final class Tags {

  private Tags() {}

  /**
   * Trims each name, joins runs of spaces into one, drops blanks, and keeps the first of names that
   * differ only in case. Null gives an empty list.
   */
  public static List<String> normalize(List<String> names) {
    if (names == null) {
      return List.of();
    }
    Map<String, String> byLowercase = new LinkedHashMap<>();
    for (String name : names) {
      if (name == null) {
        continue;
      }
      String tidy = name.strip().replaceAll("\\s+", " ");
      if (!tidy.isEmpty()) {
        byLowercase.putIfAbsent(tidy.toLowerCase(Locale.ROOT), tidy);
      }
    }
    return List.copyOf(byLowercase.values());
  }
}
