package app.ari.api.feature.account.domain.model;

/** Where a refresh token lives: in the website's cookies or in the browser extension (F13). */
public enum TokenClient {
  WEB,
  EXTENSION
}
