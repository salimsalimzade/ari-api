package app.ari.api.feature.account.domain.model;

import java.util.Locale;

/** Usernames are stored, compared and filtered in one form: trimmed and lowercase. */
public final class Usernames {

  private Usernames() {}

  public static String normalize(String username) {
    return username.strip().toLowerCase(Locale.ROOT);
  }
}
