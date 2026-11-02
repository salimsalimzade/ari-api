package app.ari.api.feature.account.domain.model;

/** A new account, or why there is none; {@code user} when CREATED. */
public record Registration(Status status, User user) {

  public enum Status {
    CREATED,
    USERNAME_TAKEN,
    /** Supabase's password rules refused it. */
    WEAK_PASSWORD
  }
}
