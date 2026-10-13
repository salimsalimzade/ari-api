package app.ari.api.feature.account.domain.service;

/** Whether a username is free, answered by the Bloom filter first and the database second. */
public interface UsernameService {

  /** Counts one check against 30 a minute per address; false when over. */
  boolean mayCheck(String clientAddress);

  /** True when no account has the username, in any case. */
  boolean isAvailable(String username);

  /** Puts a username that was just taken into the filter. */
  void remember(String username);

  /** Fills the filter from the users table when it is missing; returns how many it holds, or -1. */
  int fillFilterIfMissing();

  /** Builds the filter again, which drops usernames nobody has any more; returns how many. */
  int rebuildFilter();
}
