package app.ari.api.feature.account.domain.model;

/** A user's billing plan and how many highlights it can store at once. */
public enum Plan {
  FREE(500),
  /** No practical limit; billing (F15) decides how the API shows it. */
  PRO(Integer.MAX_VALUE);

  private final int savesLimit;

  Plan(int savesLimit) {
    this.savesLimit = savesLimit;
  }

  public int savesLimit() {
    return savesLimit;
  }
}
