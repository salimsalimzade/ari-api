package app.ari.api.feature.account.domain.model;

import java.util.Locale;
import java.util.UUID;

/**
 * Supabase Auth signs people in by email, but an Ari account needs none: until its user verifies
 * one, its Supabase user has this placeholder address. {@code .invalid} is reserved, so it can
 * never reach anybody, and Ari never shows it. The profile trigger (changeset 012) knows the domain
 * too.
 */
public final class NoEmail {

  static final String DOMAIN = "@no-email.ari.invalid";

  private NoEmail() {}

  public static String of(UUID userId) {
    return userId + DOMAIN;
  }

  public static boolean is(String email) {
    return email == null || email.toLowerCase(Locale.ROOT).endsWith(DOMAIN);
  }
}
