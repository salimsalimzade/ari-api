package app.ari.api.feature.account.domain.model;

/** What a reset link's code is now; {@code user} when VALID. */
public record ResetLink(Status status, User user) {

  public enum Status {
    VALID,
    /** Older than its 30 minutes. */
    EXPIRED,
    /** It changed a password already. */
    USED,
    /** Unknown, or so old it was forgotten. */
    INVALID
  }
}
