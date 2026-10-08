package app.ari.api.feature.account.application.model.response;

/** A token pair for the presentation layer to put into cookies; never sent as JSON. */
public record AuthTokens(String accessToken, String refreshToken) {}
