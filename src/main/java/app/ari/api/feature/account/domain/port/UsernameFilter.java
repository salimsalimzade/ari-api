package app.ari.api.feature.account.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A Bloom filter of taken usernames: it answers "definitely free" or "maybe taken". A name it holds
 * by mistake only costs a database query; a name it misses would wrongly look free, so it is never
 * asked while it is missing or unreachable.
 */
public interface UsernameFilter {

  /** Empty when the filter can't answer (it is missing or Redis is down): ask the database. */
  Optional<Boolean> mightContain(String username);

  /** Best effort; does nothing while the filter is missing, so it never starts an empty one. */
  void add(String username);

  void addAll(Collection<String> usernames);

  boolean isPresent();

  /** Builds a new filter from the pages of usernames and swaps it in; returns how many it holds. */
  int rebuild(Stream<List<String>> pages);
}
