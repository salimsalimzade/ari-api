package app.ari.api.feature.account.application.model.response;

/** Result of registering or signing in: the user for the body, the tokens for the cookies. */
public record SignedIn(MeResponse me, AuthTokens tokens) {}
