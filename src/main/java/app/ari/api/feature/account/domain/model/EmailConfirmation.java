package app.ari.api.feature.account.domain.model;

/** How opening a verify link ended. */
public enum EmailConfirmation {
  /** The pending email is now the user's verified email. */
  CONFIRMED,
  /** Unknown, expired or used link, or the user has since asked to verify another address. */
  INVALID_CODE,
  /** Another account verified this address first. */
  EMAIL_TAKEN
}
