package app.ari.api.feature.account.domain.model;

/** A user's plan. There is one plan, Hive, with a free trial; there is no free plan. */
public enum Plan {
  /** No subscription yet, or it ended: the user can read and export, but not save or import. */
  NONE,
  /** The Paddle subscription is trialing, active or past due. */
  HIVE
}
