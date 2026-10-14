package app.ari.api.feature.account.domain.model;

/** The account a reset link would go to, or why it can't go anywhere; {@code user} when FOUND. */
public record ResetRecipient(Status status, User user) {

  public enum Status {
    /** The account has a verified email to send the link to. */
    FOUND,
    /** No account has that username or verified email. */
    NO_ACCOUNT,
    /** The account has no email at all. */
    NO_EMAIL,
    /** Its email (or the one asked for) still waits for its verify link. */
    EMAIL_NOT_CONFIRMED
  }
}
